package es.ulpgc.dacd.skydelay.weather.model;

import java.time.Instant;

public record Weather(
        Instant ts,
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
) {
    @Override
    public String toString() {
        return String.format("""
                Weather at %s airport
                Status: %s
                Temperature: %.2f°C (Feels like: %.2f°C)
                Humidity: %d%%
                Visibility: %d meters
                Wind: %.2f m/s (Gusts: %.2f m/s)
                Cloudiness: %d%%
                """,
                airport, description, temp, feelsLike, humidity, visibility, windSpeed, windGust, cloudsPct
        );
    }
}