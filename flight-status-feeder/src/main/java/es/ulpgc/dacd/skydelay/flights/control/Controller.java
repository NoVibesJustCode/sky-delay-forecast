package es.ulpgc.dacd.skydelay.flights.control;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class Controller {
    private final FlightCrawler crawler;
    private final FlightScraper scraper;
    private final FlightStore store;
    private final String linksPath;

    public Controller(FlightCrawler crawler, FlightScraper scraper, FlightStore store, String linksPath) {
        this.crawler = crawler;
        this.scraper = scraper;
        this.store = store;
        this.linksPath = linksPath;
    }

    public void execute() {
        Timer timer = new Timer("Flight-System-Timer");

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                System.out.println("[CRAWLER]...");
                Map<String, List<String>> linksMap = crawler.getDomesticFlightLinks();

                List<String> newLinks = linksMap.values().stream()
                        .flatMap(List::stream)
                        .distinct()
                        .toList();

                try {
                    LinkManager linkManager = new LinkManager(linksPath);
                    linkManager.saveUniqueLinks(newLinks);
                    System.out.println("[CRAWLER] ✅ Links in queue: " + linkManager.getPendingLinks().size());
                } catch (IOException e) {
                    System.err.println("[CRAWLER] ❌ Error: " + e.getMessage());
                }
            }
        }, 0, 6 * 60 * 60 * 1000L);

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                System.out.println("[SCRAPER]...");
                scraper.startCapture(store, linksPath);
            }
        }, 10 * 60 * 1000L, 20 * 60 * 1000L);
    }
}

