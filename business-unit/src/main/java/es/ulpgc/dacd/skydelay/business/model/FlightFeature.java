package es.ulpgc.dacd.skydelay.business.model;

public record FlightFeature(
        double temp,
        double wind,
        double vis,
        int distance,
        String category)
{}
