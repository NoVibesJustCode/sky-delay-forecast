package es.ulpgc.dacd.skydelay.flights.control;

import es.ulpgc.dacd.skydelay.flights.model.Flight;

import java.util.*;

public class Controller {
    private final FlightCrawler crawler;
    private final FlightScraper scraper;
    private final FlightStore store;
    private final String path;

    public Controller(FlightCrawler crawler, FlightScraper scraper, FlightStore store, String path) {
        this.crawler = crawler;
        this.scraper = scraper;
        this.store = store;
        this.path = path;
    }

    public void start() {
        Timer timer = new Timer("Flight-Timer");
        long period = 6 * 60 * 60 * 1000L;

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    execute();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }, 0, period);
    }

    public void execute() throws InterruptedException {
        System.out.println("--- Starting Flight Discovery Cycle ---");

        for (int i = 0; i < 20; i++){
            scraper.startCapture(store, path);
            Thread.sleep(5000 + new Random().nextInt(3000));
        }

        System.out.println("--- Cycle Completed ---");
    }
}