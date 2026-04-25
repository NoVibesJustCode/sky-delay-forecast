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
        return String.format(
                """
                        Meteorología en el aeropuerto de %s\s
                        Estado: %s
                        Temperatura: %.2f°C (Sensación: %.2f°C)
                        Humedad: %d%%
                        Visibilidad: %d metros
                        Viento: %.2f m/s (Ráfagas: %.2f m/s)
                        Porcentaje de nubosidad: %d%%
                        """,
                airport, description, temp, feelsLike, humidity, visibility, windSpeed, windGust, cloudsPct
        );
    }
}

