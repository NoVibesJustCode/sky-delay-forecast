package es.ulpgc.dacd.skydelay.flights.control;

import com.microsoft.playwright.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FlighteraScraper {
    public static void main(String[] args) {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
            Page page = browser.newPage();
            String url = "https://www.flightera.net/en/flight_details/Vueling-Las+Palmas-Malaga/VY3067/GCLP/2026-03-20";
            scrapFlight(url, page);
            browser.close();
        }
    }

    private static void scrapFlight(String flightURL, Page page){
        page.navigate(flightURL);
        page.waitForSelector("h1[itemprop='flightNumber']");
        String flightId = page.locator("h1[itemprop='flightNumber']").innerText().trim();
        String date = page.locator("[itemprop='departureTime']").first().innerText().trim();
        String origin = page.locator("[itemprop='departureAirport'] [itemprop='iataCode']").innerText().trim();
        String destination = page.locator("[itemprop='arrivalAirport'] [itemprop='iataCode']").innerText().trim();
        String depTimeUTC = extractTimeUTC(page.locator("#depTimeLiveHB + div").innerText());
        String arrTimeUTC = extractTimeUTC(page.locator("#arrTimeLiveHB + div").innerText());
        String status = page.locator("#liveStatusInd").innerText().trim();
        String depDelay = cleanDelay(page.locator("#depDelHB").innerText());
        String arrDelay = cleanDelay(page.locator("#arrDelHB").innerText());
        String distance = cleanDistance(page.locator("[itemprop='distance']").first().innerText());
        String aircraftModel = cleanAircraft(page.locator("[itemprop='model']").first().innerText());
    }

    private static String cleanDelay(String text) {
        if (text == null) return "0";
        return text.replace("+", "").replace("-", "").trim();
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