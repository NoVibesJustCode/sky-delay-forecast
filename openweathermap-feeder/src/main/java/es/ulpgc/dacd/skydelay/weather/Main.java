package es.ulpgc.dacd.skydelay.weather;

import es.ulpgc.dacd.skydelay.weather.control.*;
import es.ulpgc.dacd.skydelay.weather.model.Airport;

import jakarta.jms.JMSException;
import java.util.List;


public class Main {
    public static void main(String[] args) throws JMSException {
        AirportsReader reader = new AirportsReader(args[0]);
        List<Airport> airports = reader.read();
        WeatherParser parser = new WeatherParser();
        WeatherFeeder feeder = new OpenWeatherMapFeeder(parser);
        WeatherStore store = new WeatherPublisher(args[1], "weather");
        Control control = new Control(feeder, store, airports);

        System.out.println("Iniciando envío de datos periódico...");
        control.start();
    }

}