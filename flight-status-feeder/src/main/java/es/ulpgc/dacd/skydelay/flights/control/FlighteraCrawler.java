package es.ulpgc.dacd.skydelay.flights.control;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;

public class FlighteraCrawler implements FlightCrawler {
    private static final List<String> ICAO_SPAIN_AIRPORTS = Arrays.asList(
            "LEMD", "LEBL", "LEPA", "LEMG", "LEAL", "LEVC", "LEZL",
            "GCLP", "GCXO", "GCTS", "GCFV", "GCRR", "GCLA", "GCHI",
            "LEBB", "LEST", "LECO", "LEVX", "LEAS", "LEXJ", "LEIB",
            "LEMH", "LEGE", "LEGR", "LEAM", "LEJR", "LEBZ", "GEML"
    );

    @Override
    public Map<String, List<String>> getDomesticFlightLinks(){
        Map<String, List<String>> flightLinksByAirport = new HashMap<>();
        Random rand = new Random();
        Collections.shuffle(ICAO_SPAIN_AIRPORTS);

        for (String code : ICAO_SPAIN_AIRPORTS) {
            String airportUrl = "https://www.flightera.net/en/airport/_/" + code + "/departure";
            List<String> links = crawlAirport(airportUrl);
            flightLinksByAirport.put(code, links);
            try {
                Thread.sleep(10000 + rand.nextInt(8000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return flightLinksByAirport;
    }

    public static List<String> crawlAirport(String airportUrl) {
        try {
            Document doc = Jsoup.connect(airportUrl).userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .get();

            return doc.select("table tbody tr").stream()
                    .filter(FlighteraCrawler::isSpainFlight)
                    .map(FlighteraCrawler::extractFlightUrl)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return List.of();
    }

    private static boolean isSpainFlight(Element row) {
        return row.selectFirst("img[alt*='Spain']") != null;
    }

    private static String extractFlightUrl(Element row) {
        Element flightLink = row.select("td").last().selectFirst("a");
        return flightLink != null ? "https://www.flightera.net" + flightLink.attr("href") : null;
    }
}











