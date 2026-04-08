package es.ulpgc.dacd.skydelay.weather.control;

import es.ulpgc.dacd.skydelay.weather.model.Airport;
import es.ulpgc.dacd.skydelay.weather.model.Weather;

import java.util.List;

public interface WeatherFeeder {
    List<Weather> fetch(Airport airport);
}
