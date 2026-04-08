package es.ulpgc.dacd.skydelay.weather;

import es.ulpgc.dacd.skydelay.weather.control.*;
import es.ulpgc.dacd.skydelay.weather.model.Airport;
import es.ulpgc.dacd.skydelay.weather.model.Weather;

import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        AirportsReader reader = new AirportsReader();
        List<Airport> airports = reader.read();
        WeatherParser parser = new WeatherParser();
        WeatherFeeder feeder = new OpenWeatherMapFeeder(parser);
        WeatherStore store = new SqliteWeatherStore("storage/db/weather.db");
        Control control = new Control(feeder, store, airports);

        System.out.println("Iniciando proceso de persistencia...");
        control.execute();
    }

}