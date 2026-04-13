package es.ulpgc.dacd.skydelay.flights.control;

import java.util.List;
import java.util.Map;

public class Controller {
    private final FlightCrawler crawler;
    private final FlightScraper scraper;
    private final FlightStore store;

    public Controller(FlightCrawler crawler, FlightScraper scraper, FlightStore store) {
        this.crawler = crawler;
        this.scraper = scraper;
        this.store = store;
    }

    public void execute() {
        System.out.println("Starting Flight Discovery...");
        Map<String, List<String>> domesticLinks = crawler.getDomesticFlightLinks();

        List<String> allLinks = domesticLinks.values().stream()
                .flatMap(List::stream)
                .distinct()
                .toList();

        System.out.println("Found " + allLinks.size() + " domestic links. Starting scraping...");

        for (String url : allLinks) {
            // Aquí llamarías a tu lógica de Playwright a través de la interfaz
            // Nota: Si mantienes el parámetro Page, el Controller debería gestionarlo o el Scraper internamente
            // Optional<Flight> flight = scraper.scrapFlight(url, page);
            // flight.ifPresent(publisher::publish);
        }
    }
}