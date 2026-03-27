package es.ulpgc.dacd.skydelay.weather;

import es.ulpgc.dacd.skydelay.weather.control.*;
import es.ulpgc.dacd.skydelay.weather.model.Weather;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        AirportsReader reader = new AirportsReader();
        WeatherParser parser = new WeatherParser();

        WeatherFeeder feeder = new OpenWeatherMapFeeder(reader, parser);
        WeatherStore store = new SqliteWeatherStore("storage/db/weather.db");

        Control control = new Control(feeder, store);
        control.execute();
    }
}