package es.ulpgc.dacd.skydelay.weather.control;

import es.ulpgc.dacd.skydelay.weather.model.Airport;

import java.util.List;

public interface WeatherFeeder {
    List<Object> fetch(Airport airport);
}
