package es.ulpgc.dacd.skydelay.weather.control;

import es.ulpgc.dacd.skydelay.weather.model.Airport;

import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class Controller {
    private final WeatherFeeder feeder;
    private final WeatherStore store;
    private final List<Airport> airports;

    public Controller(WeatherFeeder feeder, WeatherStore store, List<Airport> airports) {
        this.feeder = feeder;
        this.store = store;
        this.airports = airports;
    }


    public void start() {
        Timer timer = new Timer("Weather-Timer");

        long delay = 0;
        long period = 60 * 60 * 1000L;

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                execute();
            }
        }, delay, period);

        System.out.println("Scheduled control: sending information every 3 hours.");
    }

    public void execute() {
        int totalSaved = 0;

        totalSaved += airports.stream()
                .map(feeder::fetch)
                .filter(Objects::nonNull)
                .peek(events -> events.forEach(store::save))
                .mapToInt(List::size)
                .sum();

        System.out.println("Process completed. Total events sent: " + totalSaved);
    }
}