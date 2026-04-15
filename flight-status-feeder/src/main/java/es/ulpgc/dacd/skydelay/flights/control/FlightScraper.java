package es.ulpgc.dacd.skydelay.flights.control;

public interface FlightScraper {
    void startCapture(FlightStore store, String textFilePath);
}
