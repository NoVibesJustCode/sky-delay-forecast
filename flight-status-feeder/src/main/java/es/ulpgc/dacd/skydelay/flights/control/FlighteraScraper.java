package es.ulpgc.dacd.skydelay.flights.control;

import com.microsoft.playwright.*;
import es.ulpgc.dacd.skydelay.flights.model.Flight;
import java.util.*;
import java.util.stream.Collectors;

public class FlighteraScraper {
    private static final int BATCH_SIZE = 10;

    public static void main(String[] args) {
        LinkManager linkManager = new LinkManager();
        FlightPublisher publisher = new FlightPublisher();
        Random random = new Random();

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
            Page page = browser.newPage();

            List<String> pending = linkManager.getPendingLinks();
            if (pending.isEmpty()) {
                FlighteraCrawler crawler = new FlighteraCrawler();
                List<String> allLinks = crawler.getDomesticFlightLinks().values().stream()
                        .flatMap(List::stream).distinct().collect(Collectors.toList());
                linkManager.saveLinks(allLinks);
                pending = allLinks;
            }

            List<String> currentBatch = pending.stream().limit(BATCH_SIZE).toList();
            List<String> processed = new ArrayList<>();

            for (String url : currentBatch) {
                try {
                    Flight flight = scrapFlight(url, page);
                    publisher.publish(flight);
                    System.out.println(flight);
                    processed.add(url);
                    Thread.sleep(7000 + random.nextInt(3000));
                } catch (Exception e) {
                    System.err.println("Error en " + url + ": " + e.getMessage());
                }
            }

            linkManager.removeProcessedLinks(processed);
            browser.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Flight scrapFlight(String flightURL, Page page) {
        page.navigate(flightURL);
        handleCookies(page);

        page.waitForSelector("h1[itemprop='flightNumber']");

        return new Flight(
                page.locator("h1[itemprop='flightNumber']").innerText().trim(),
                page.locator("[itemprop='departureAirport'] [itemprop='iataCode']").innerText().trim(),
                page.locator("[itemprop='arrivalAirport'] [itemprop='iataCode']").innerText().trim(),
                page.locator("[itemprop='departureTime']").first().innerText().trim(),
                FlightMapper.extractTimeUTC(page.locator("#depTimeLiveHB + div").innerText()),
                FlightMapper.extractTimeUTC(page.locator("#arrTimeLiveHB + div").innerText()),
                page.locator("#liveStatusInd").innerText().trim(),
                FlightMapper.parseDelay(page.locator("#depDelHB").count() > 0 ? page.locator("#depDelHB").innerText() : "0"),
                FlightMapper.parseDelay(page.locator("#arrDelHB").count() > 0 ? page.locator("#arrDelHB").innerText() : "0"),
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