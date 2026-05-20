package es.ulpgc.dacd.skydelay.flights.control;

import com.microsoft.playwright.*;
import es.ulpgc.dacd.skydelay.flights.model.Flight;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

public class FlighteraScraper implements FlightScraper {
    private static final int BATCH_SIZE = 20;
    private static final Logger logger = LoggerFactory.getLogger(FlighteraScraper.class);

    @Override
    public void startCapture(FlightStore store, String textFilePath) {
        LinkManager linkManager = new LinkManager(textFilePath);
        Random random = new Random();

        String chromePath = Dotenv.load().get("CHROME_EXECUTABLE_PATH");
        String userDataDir = Dotenv.load().get("CHROME_USER_DATA");

        deleteUserDataDir(userDataDir);

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

            linkManager.removeProcessedLinks(processed, currentBatch);
            context.close();
        } catch (Exception e) {
            logger.error("Critical error during Playwright execution", e);
        }
        deleteUserDataDir(userDataDir);
    }

    private Flight scrapFlight(String flightURL, Page page) {
        page.navigate(flightURL);

        page.waitForSelector("h1[itemprop='flightNumber']");

        String dateFromPage = page.locator("[itemprop='departureTime']").first().innerText().trim();
        String dateFromUrl = extractDateFromUrl(flightURL);
        String flightDate = (dateFromUrl != null) ? dateFromUrl : dateFromPage;

        return new Flight(
                Instant.now(),
                "flight-feeder",
                page.locator("h1[itemprop='flightNumber']").innerText().trim(),
                page.locator("[itemprop='departureAirport'] [itemprop='iataCode']").innerText().trim(),
                page.locator("[itemprop='arrivalAirport'] [itemprop='iataCode']").innerText().trim(),
                flightDate,
                FlightMapper.extractTimeUTC(extractUTCBlock(page, "#depTimeLiveHB")),
                FlightMapper.extractTimeUTC(extractUTCBlock(page, "#arrTimeLiveHB")),
                page.locator("#liveStatusInd").innerText().trim(),
                FlightMapper.parseDelay(page.locator("#depDelOuterHB").count() > 0 ? page.locator("#depDelOuterHB").innerText() : "0"),
                FlightMapper.parseDelay(page.locator("#arrDelOuterHB").count() > 0 ? page.locator("#arrDelOuterHB").innerText() : "0"),
                FlightMapper.parseDistance(page.locator("[itemprop='distance']").first().innerText()),
                FlightMapper.cleanAircraft(page.locator("[itemprop='model']").count() > 0 ? page.locator("[itemprop='model']").innerText() : "Unknown")
        );
    }

    private static String extractUTCBlock(Page page, String anchorId) {
        String js = """
            (() => {
                const anchor = document.querySelector('%s');
                if (!anchor) return '';
                const block = anchor.parentElement;
                if (!block) return '';
                const match = block.innerText.match(/(\\d{2}:\\d{2})\\s*UTC/);
                return match ? match[0] : '';
            })()
            """.formatted(anchorId);
        return page.evaluate(js).toString();
    }

    private static String extractDateFromUrl(String url) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d{4}-\\d{2}-\\d{2})$").matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private void deleteUserDataDir(String path) {
        try {
            Path directory = Paths.get(path);
            if (Files.exists(directory)) {
                Files.walk(directory)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                System.out.println("[CLEANUP] user-data-dir deleted to reset session.");
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Could not delete user-data-dir: " + e.getMessage());
        }
    }
}