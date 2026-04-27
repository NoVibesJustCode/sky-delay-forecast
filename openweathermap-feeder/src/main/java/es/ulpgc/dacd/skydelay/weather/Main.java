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
        WeatherStore store = new ActiveMQWeatherStore(args[1], "weather");
        Controller controller = new Controller(feeder, store, airports);

        System.out.println("Starting periodic data transmission...");
        controller.start();
    }

}