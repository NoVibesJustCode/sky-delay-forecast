package es.ulpgc.dacd.skydelay.weather.control;

import es.ulpgc.dacd.skydelay.weather.model.Weather;

public interface WeatherStore {
    void save(Object weatherEvent);
    void close();
}
