package es.ulpgc.dacd.skydelay.flights.control;

import com.microsoft.playwright.*;
import es.ulpgc.dacd.skydelay.flights.model.Flight;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FlighteraScraper {
    public static void main(String[] args) {
        List<Flight> collectedFlights = new ArrayList<>();
        Random random = new Random();

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
            Page page = browser.newPage();

            FlighteraCrawler crawler = new FlighteraCrawler();
            Map<String, List<String>> allFlightLinks = crawler.getDomesticFlightLinks();
            for (List<String> links : allFlightLinks.values()) {
                for (String url : links) {
                    try {
                        Flight flight = scrapFlight(url, page);
                        collectedFlights.add(flight);
                        Thread.sleep(7000 + random.nextInt(3000));
                    }
                    catch (Exception e) {
                    System.err.println("Error en link: " + url + " -> " + e.getMessage());
                    }
                }
            }
            browser.close();
            collectedFlights.forEach(System.out::println);
        }
    }

    private static Flight scrapFlight(String flightURL, Page page){
        page.navigate(flightURL);

        FrameLocator cookieFrame = page.frameLocator("iframe[id^='sp_message_iframe']");
        Locator rejectButton = cookieFrame.locator("button[title='Rechazar todo']");

        try {
            rejectButton.waitFor(new Locator.WaitForOptions().setTimeout(3000));

            if (rejectButton.isVisible()) {
                rejectButton.click();
                page.waitForCondition(() -> !rejectButton.isVisible());
            }
        } catch (Exception ignored) {
            Locator fallback = page.locator("button:has-text('Rechazar todo')").first();
            if (fallback.isVisible()) fallback.click();
        }

        page.waitForSelector("h1[itemprop='flightNumber']");
        String flightId = page.locator("h1[itemprop='flightNumber']").innerText().trim();
        String date = page.locator("[itemprop='departureTime']").first().innerText().trim();
        String origin = page.locator("[itemprop='departureAirport'] [itemprop='iataCode']").innerText().trim();
        String destination = page.locator("[itemprop='arrivalAirport'] [itemprop='iataCode']").innerText().trim();
        String depTimeUTC = extractTimeUTC(page.locator("#depTimeLiveHB + div").innerText());
        String arrTimeUTC = extractTimeUTC(page.locator("#arrTimeLiveHB + div").innerText());
        String status = page.locator("#liveStatusInd").innerText().trim();
        Locator depDelayLoc = page.locator("#depDelHB");
        int depDelay = (depDelayLoc.count() > 0) ? Integer.parseInt(cleanDelay(depDelayLoc.innerText())) : 0;
        Locator arrDelayLoc = page.locator("#arrDelHB");
        int arrDelay = (arrDelayLoc.count() > 0) ? Integer.parseInt(cleanDelay(arrDelayLoc.innerText())) : 0;
        int distance = Integer.parseInt(cleanDistance(page.locator("[itemprop='distance']").first().innerText()));
        Locator aircraftLocator = page.locator("[itemprop='model']").first();
        String aircraftModel = (aircraftLocator.count() > 0) ? cleanAircraft(aircraftLocator.innerText()) : "Unknown";

        return new Flight(flightId, origin, destination, date, depTimeUTC,
                arrTimeUTC, status, depDelay, arrDelay, distance, aircraftModel);
    }

    private static String cleanDelay(String text) {
        if (text == null || text.trim().isEmpty()) return "0";
        return text.replace("+", "").trim();
    }

    private static String extractTimeUTC(String text) {
        Pattern pattern = Pattern.compile("(\\d{2}:\\d{2})");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "N/A";
    }

    private static String cleanDistance(String text) {
        if (text == null || text.isEmpty()) return "N/A";
        String firstPart = text.split("/")[0].trim();
        return firstPart.replace("km", "").replace(",", "").trim();
    }

    private static String cleanAircraft(String text) {
        if (text == null) return "Unknown";
        return text.trim();
    }
}