package es.ulpgc.dacd.skydelay.weather;

import es.ulpgc.dacd.skydelay.weather.control.*;
import es.ulpgc.dacd.skydelay.weather.model.Airport;

import jakarta.jms.JMSException;
import java.util.List;


public class Main {
    public static void main(String[] args) throws JMSException {
        WeatherStore store = new ActiveMQWeatherStore(args[0], args[1], args[2]);
        AirportsReader reader = new AirportsReader(args[3]);
        List<Airport> airports = reader.read();
        WeatherParser parser = new WeatherParser();
        WeatherFeeder feeder = new OpenWeatherMapFeeder(parser);
        Controller controller = new Controller(feeder, store, airports);

        System.out.println("Starting periodic data transmission...");
        controller.start();
    }

}