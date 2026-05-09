package es.ulpgc.dacd.skydelay.business.model;

public record FlightFeature(
        String flightId,
        String originIcao,
        String destIcao,

        double depTemp,
        double depFeelsLike,
        int depHumidity,
        int depVisibility,
        double depWindSpeed,
        double depWindGust,
        int depClouds,

        double arrTemp,
        double arrFeelsLike,
        int arrHumidity,
        int arrVisibility,
        double arrWindSpeed,
        double arrWindGust,
        int arrClouds,

        int distanceKm,
        int departureDelay,
        int arrivalDelay,
        String delayCategory
) {}