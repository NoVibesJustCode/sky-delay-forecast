package es.ulpgc.dacd.skydelay.flights.model;

import java.time.Instant;

public record Flight (
    Instant ts,
    String ss,
    String flightId,
    String origin,
    String destination,
    String date,
    String departureTimeUTC,
    String arrivalTimeUTC,
    String status,
    int departureDelay,
    int arrivalDelay,
    int distanceKm,
    String aircraftModel
) {
    @Override
    public String toString() {
        return String.format("Flight %s: %s -> %s | Total Delay: %dm | Aircraft: %s",
                flightId, origin, destination, departureDelay + arrivalDelay, aircraftModel);
    }
}