package es.ulpgc.dacd.skydelay.weather.control;

import es.ulpgc.dacd.skydelay.weather.model.Airport;
import es.ulpgc.dacd.skydelay.weather.model.Weather;

import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;


public class Control {
    private final WeatherFeeder feeder;
    private final WeatherStore store;
    private final List<Airport> airports;

    public Control(WeatherFeeder feeder, WeatherStore store, List<Airport> airports) {
        this.feeder = feeder;
        this.store = store;
        this.airports = airports;
    }


    public void start() {
        Timer timer = new Timer("Weather-Timer");

        long delay = 0;
        long period = 6 * 60 * 60 * 1000L;

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                execute();
            }
        }, delay, period);

        System.out.println("Control programado: enviando información cada 6 horas.");
    }

    public void execute() {
        int totalSaved = 0;

        totalSaved += airports.stream()
                .map(feeder::fetch)
                .filter(Objects::nonNull)
                .peek(weathers -> weathers.forEach(store::save))
                .mapToInt(List::size)
                .sum();
        System.out.println("Proceso de guardado finalizado para " + totalSaved + " registros.");
    }
}