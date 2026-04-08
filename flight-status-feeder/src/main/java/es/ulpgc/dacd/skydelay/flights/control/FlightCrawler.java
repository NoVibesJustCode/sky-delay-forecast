package es.ulpgc.dacd.skydelay.flights.control;

import java.util.List;
import java.util.Map;

public interface FlightCrawler {
    Map<String, List<String>> getDomesticFlightLinks();
}
