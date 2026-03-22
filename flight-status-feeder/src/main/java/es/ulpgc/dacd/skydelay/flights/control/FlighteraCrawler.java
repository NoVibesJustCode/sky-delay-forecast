package es.ulpgc.dacd.skydelay.flights.control;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;

public class FlighteraCrawler {
    private static final List<String> ICAO_SPAIN_AIRPORTS = Arrays.asList(
            "LEMD", "LEBL", "LEPA", "LEMG", "LEAL", "LEVC", "LEZL",
            "GCLP", "GCXO", "GCTS", "GCFV", "GCRR", "GCLA", "GCHI",
            "LEBB", "LEST", "LECO", "LEVX", "LEAS", "LEXJ", "LEIB",
            "LEMH", "LEGE", "LEGR", "LEAM", "LEJR", "LEBZ", "GEML"
    );

    public Map<String, List<String>> getDomesticFlightLinks(){
        Map<String, List<String>> flightLinksByAirport = new HashMap<>();
        Random rand = new Random();
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

    public static List<String> crawlAirport(String airportUrl){
        List<String> collectedLinks = new ArrayList<>();
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
                    collectedLinks.add(flightDetails);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return collectedLinks;
    }
}











