package es.ulpgc.dacd.skydelay.flights;

import es.ulpgc.dacd.skydelay.flights.control.*;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        FlightCrawler crawler = new FlighteraCrawler();
        FlightScraper scraper = new FlighteraScraper();
        FlightStore store = new SqliteFlightStore(args[0]);
        Controller controller = new Controller(crawler, scraper, store, args[1]);
        controller.execute();
    }
}