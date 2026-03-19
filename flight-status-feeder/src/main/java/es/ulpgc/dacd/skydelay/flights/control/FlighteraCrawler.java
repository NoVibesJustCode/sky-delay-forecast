package es.ulpgc.dacd.skydelay.flights.control;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.Arrays;
import java.util.List;


public class FlighteraCrawler {
    private static final List<String> ICAO_SPAIN_AIRPORTS = Arrays.asList(
            "GCRR", "GCLP", "GCFV", "GCXO", "GCTS", "GCLA", "GCHI", "GCGM",
            "LEMD", "LEBL", "LEPA", "LEMG", "LEAL", "LEVC", "LEZL", "LEIB", "LEBB", "LEST", "LEMH",
            "LEAS", "LEXJ", "LEGR", "LEVT", "LEPP", "LELN", "LESA", "LEVD", "LELO", "LEHC",
            "LEJR", "LEAM", "LERS", "LEGE", "LEBZ", "LECH", "GEML"
    );

    public static void main(String[] args) {
        for (String code : ICAO_SPAIN_AIRPORTS) {
            String airportUrl = "https://www.flightera.net/en/airport/_/" + code + "/departure";
            System.out.println("\n>>> Checking Airport: " + code);
            crawlAirport(airportUrl);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void crawlAirport(String airportUrl){
        try {
            Document doc = Jsoup.connect(airportUrl).userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .get();
            Elements rows = doc.select("table tbody tr");
            for (Element row : rows) {
                Element spainFlag = row.selectFirst("img[alt*='Spain']");
                if (spainFlag != null) {
                    Element flightLink = row.select("td").get(1).selectFirst("a");
                    String flightCode = flightLink.text();
                    String flightDetails = "https://www.flightera.net" + flightLink.attr("href");
                    String destination = row.select("td").get(2).text();
                    System.out.println("Flight: " + flightCode + " | Destination: " + destination);
                    System.out.println("Details: " + flightDetails);
                    System.out.println("");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}