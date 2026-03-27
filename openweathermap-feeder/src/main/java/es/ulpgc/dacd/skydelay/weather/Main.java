package es.ulpgc.dacd.skydelay.weather;

import es.ulpgc.dacd.skydelay.weather.control.AirportsReader;
import es.ulpgc.dacd.skydelay.weather.control.OpenWeatherMapFeeder;
import es.ulpgc.dacd.skydelay.weather.control.WeatherParser;
import es.ulpgc.dacd.skydelay.weather.model.Weather;

import java.util.List;

public class Main {
    public static void main(String[] args) {

        AirportsReader reader = new AirportsReader();
        WeatherParser parser = new WeatherParser();

        OpenWeatherMapFeeder feeder = new OpenWeatherMapFeeder(reader, parser);

        List<Weather> results = feeder.fetch();

        if (results.isEmpty()) {
            System.out.println("No se han podido obtener datos");
        } else {

            for (Weather w : results) {
                System.out.println(w.toString());
            }
        }

    }
}