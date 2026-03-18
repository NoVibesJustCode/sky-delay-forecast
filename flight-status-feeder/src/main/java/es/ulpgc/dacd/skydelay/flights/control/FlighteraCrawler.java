package es.ulpgc.dacd.skydelay.flights.control;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class FlighteraCrawler {
    public static void main(String[] args) {
        String url = "https://www.flightera.net/en/airport/Gran+Canaria/GCLP/departure";
        try {
            Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36").get();
            Elements rows = doc.select("table tbody tr");
            System.out.println("--- NATIONAL FLIGHTS FOUND ---");
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

