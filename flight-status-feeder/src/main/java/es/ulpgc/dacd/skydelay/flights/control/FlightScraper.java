package es.ulpgc.dacd.skydelay.flights.control;

import java.nio.file.Path;

public interface FlightScraper {
    void startCapture(FlightStore store, String textFilePath);
}
