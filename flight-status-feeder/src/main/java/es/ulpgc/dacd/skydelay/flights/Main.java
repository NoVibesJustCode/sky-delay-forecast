package es.ulpgc.dacd.skydelay.flights;

import es.ulpgc.dacd.skydelay.flights.control.*;

public class Main {
    public static void main(String[] args) {
        SqliteFlightStore repository = new SqliteFlightStore(args[0]);
        FlightPublisher publisher = new FlightPublisher(repository);
        FlightCrawler crawler = new FlighteraCrawler();
        FlightScraper scraper = new FlighteraScraper();
        Controller controller = new Controller(crawler, scraper, publisher);
        controller.execute();
    }
}