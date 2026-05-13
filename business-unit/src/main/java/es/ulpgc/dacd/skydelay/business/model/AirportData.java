package es.ulpgc.dacd.skydelay.business.model;

public record AirportData(
        String icao,
        String iata,
        String name,
        double lat,
        double lon)
{}
