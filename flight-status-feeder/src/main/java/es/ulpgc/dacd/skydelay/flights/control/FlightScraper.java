package es.ulpgc.dacd.skydelay.flights.control;

import com.microsoft.playwright.Page;
import es.ulpgc.dacd.skydelay.flights.model.Flight;

public interface FlightScraper {
    void startCapture(FlightPublisher publisher);
}
