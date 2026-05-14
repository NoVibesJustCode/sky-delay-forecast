package es.ulpgc.dacd.skydelay.flights.control;

import com.microsoft.playwright.*;
import es.ulpgc.dacd.skydelay.flights.model.Flight;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

public class FlighteraScraper implements FlightScraper {
    private static final int BATCH_SIZE = 10;
    private static final Logger logger = LoggerFactory.getLogger(FlighteraScraper.class);

    @Override
    public void startCapture(FlightStore store, String textFilePath) {
        LinkManager linkManager = new LinkManager(textFilePath);
        Random random = new Random();

        String chromePath = Dotenv.load().get("CHROME_EXECUTABLE_PATH");
        String userDataDir = Dotenv.load().get("CHROME_USER_DATA");

        if (chromePath == null || userDataDir == null) {
            throw new RuntimeException("Missing browser environment variables (CHROME_EXECUTABLE_PATH or CHROME_USER_DATA)");
        }

        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
                    .setHeadless(false)
                    .setExecutablePath(Paths.get(chromePath))
                    .setLocale("en-US")
                    .setArgs(List.of("--disable-blink-features=AutomationControlled"));

            BrowserContext context = playwright.chromium().launchPersistentContext(
                    Paths.get(userDataDir),
                    options
            );

            Page page = context.newPage();

            List<String> pending = linkManager.getPendingLinks();
            List<String> currentBatch = pending.stream().limit(BATCH_SIZE).toList();
            List<String> processed = new ArrayList<>();

            for (String url : currentBatch) {
                try {
                    Flight flight = scrapFlight(url, page);
                    store.save(flight);
                    logger.info("Successfully scraped and saved flight: {}", flight);
                    if (flight.status().equalsIgnoreCase("Landed")) processed.add(url);
                    Thread.sleep(15000 + random.nextInt(10000));
                } catch (Exception e) {
                    logger.error("Failed to process flight at URL {}: {}", url, e.getMessage());
                }
            }

            linkManager.removeProcessedLinks(processed);
            context.close();
        } catch (Exception e) {
            logger.error("Critical error during Playwright execution", e);
        }
    }

    private Flight scrapFlight(String flightURL, Page page) {
        page.navigate(flightURL);
        handleCookies(page);

        page.waitForSelector("h1[itemprop='flightNumber']");

        return new Flight(
                Instant.now(),
                "flight-feeder",
                page.locator("h1[itemprop='flightNumber']").innerText().trim(),
                page.locator("[itemprop='departureAirport'] [itemprop='iataCode']").innerText().trim(),
                page.locator("[itemprop='arrivalAirport'] [itemprop='iataCode']").innerText().trim(),
                page.locator("[itemprop='departureTime']").first().innerText().trim(),
                FlightMapper.extractTimeUTC(page.locator("#depTimeLiveHB + div").innerText()),
                FlightMapper.extractTimeUTC(page.locator("#arrTimeLiveHB + div").innerText()),
                page.locator("#liveStatusInd").innerText().trim(),
                FlightMapper.parseDelay(page.locator("#depDelOuterHB").count() > 0 ? page.locator("#depDelOuterHB").innerText() : "0"),
                FlightMapper.parseDelay(page.locator("#arrDelOuterHB").count() > 0 ? page.locator("#arrDelOuterHB").innerText() : "0"),
                FlightMapper.parseDistance(page.locator("[itemprop='distance']").first().innerText()),
                FlightMapper.cleanAircraft(page.locator("[itemprop='model']").count() > 0 ? page.locator("[itemprop='model']").innerText() : "Unknown")
        );
    }

    private static void handleCookies(Page page) {
        try {
            FrameLocator cookieFrame = page.frameLocator("iframe[id^='sp_message_iframe']");
            Locator btn = cookieFrame.locator("button[title='Rechazar todo']");
            if (btn.isVisible()) {
                btn.click();
                page.waitForCondition(() -> !btn.isVisible());
            }
        } catch (Exception ignored) {
            Locator fallback = page.locator("button:has-text('Rechazar todo')").first();
            if (fallback.isVisible()) fallback.click();
        }
    }
}