package es.ulpgc.dacd.skydelay.flights.model;

public record FlightEvent(
        String ts,
        String ss,
        Flight flight
) {}
