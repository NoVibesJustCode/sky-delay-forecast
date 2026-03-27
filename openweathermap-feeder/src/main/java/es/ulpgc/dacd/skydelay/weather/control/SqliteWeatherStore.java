package es.ulpgc.dacd.skydelay.weather.control;

import es.ulpgc.dacd.skydelay.weather.model.Weather;

public class SqliteWeatherStore implements WeatherStore {
    private final String dbPath;

    public SqliteWeatherStore(String dbPath) {
        this.dbPath = dbPath;
    }

    @Override
    public void save(Weather weather) {
        // Aquí va el código para insertar los objetos weather en la base de datos
    }
}
