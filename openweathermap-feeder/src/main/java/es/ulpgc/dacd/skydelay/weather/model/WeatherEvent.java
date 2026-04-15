package es.ulpgc.dacd.skydelay.weather.model;

import java.time.Instant;


public record WeatherEvent(String ts, String ss, Weather weather) {

    public WeatherEvent(Weather weather) {
        this(Instant.now().toString(), "openweathermap-feeder", weather);
    }
}
