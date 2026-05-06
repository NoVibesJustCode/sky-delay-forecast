package es.ulpgc.dacd.skydelay.weather.model;


import java.time.Instant;

public record WeatherForecast(
        Instant ts,
        Instant queryTs,
        String ss,
        String icao,
        String airport,
        String description,
        double temp,
        double feelsLike,
        int humidity,
        int visibility,
        double windSpeed,
        double windGust,
        int cloudsPct
) {}