package es.ulpgc.dacd.skydelay.flights;

import es.ulpgc.dacd.skydelay.flights.control.*;
import jakarta.jms.JMSException;

public class Main {
    public static void main(String[] args) throws JMSException {
        FlightCrawler crawler = new FlighteraCrawler();
        FlightScraper scraper = new FlighteraScraper();
        FlightStore store = new ActiveMqFlightStore(args[0], args[1]);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nClosing Broker Connection...");
            store.close();
        }));

        Controller controller = new Controller(crawler, scraper, store, args[2]);
        controller.execute();
    }
}