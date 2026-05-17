package es.ulpgc.dacd.skydelay.weather.control;

public interface WeatherStore {
    void save(Object weatherEvent);
    void close();
}
