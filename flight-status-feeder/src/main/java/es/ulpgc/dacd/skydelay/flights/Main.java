package es.ulpgc.dacd.skydelay.flights;

import es.ulpgc.dacd.skydelay.flights.control.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

public class Main {
    private static final Scanner scanner = new Scanner(System.in);
    private static final FlighteraCrawler crawler = new FlighteraCrawler();
    private static final LinkManager linkManager = new LinkManager();

    public static void main(String[] args) throws IOException {
        boolean running = true;

        while (running) {
            displayMenu();
            String choice = scanner.nextLine();

            switch (choice) {
                case "1" -> runCrawler();
                case "2" -> runScraper();
                case "0" -> {
                    System.out.println("Goodbye!");
                    running = false;
                }
                default -> System.out.println("Invalid option. Please try again.");
            }
        }
    }

    private static void displayMenu() {
        System.out.println("\n========================================");
        System.out.println("    FLIGHT CONTROL PANEL    ");
        System.out.println("====================================");
        System.out.println("1. [Crawler]  Discover new flight links");
        System.out.println("2. [Scraper]  Process pending links");
        System.out.println("0. Exit");
        System.out.print("\nSelect an option: ");
    }

    private static void runCrawler() throws IOException {
        System.out.println("\n[Action] Starting Crawler...\n");

        Map<String, List<String>> airportFlightMap = crawler.getDomesticFlightLinks();
        List<String> allLinks = airportFlightMap.values().stream()
            .flatMap(List::stream).distinct().collect(Collectors.toList());

        linkManager.saveLinks(allLinks);
        System.out.println("Total flight links discovered: " + allLinks.size());
        }

    private static void runScraper() {
        System.out.println("\n[Action] Starting Scraper...\n");
        FlighteraScraper.startCapture();
        System.out.println("\nBatch processing finished.");
    }
}