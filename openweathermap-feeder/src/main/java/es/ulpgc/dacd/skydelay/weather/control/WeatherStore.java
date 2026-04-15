package es.ulpgc.dacd.skydelay.weather.control;

import es.ulpgc.dacd.skydelay.weather.model.Weather;
import es.ulpgc.dacd.skydelay.weather.model.WeatherEvent;

public interface WeatherStore {
    void save(WeatherEvent weatherEvent);
}
