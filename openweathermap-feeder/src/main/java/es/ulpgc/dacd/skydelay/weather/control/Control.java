package es.ulpgc.dacd.skydelay.weather.control;

import es.ulpgc.dacd.skydelay.weather.model.Airport;
import es.ulpgc.dacd.skydelay.weather.model.Weather;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


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

        for (Airport airport : airports) {
            List<Weather> weathers = feeder.fetch(airport);

            if (weathers != null) {
                for (Weather weather : weathers) {
                    store.save(weather);
                }
                totalSaved += weathers.size();
            }
        }

        System.out.println("Proceso de guardado finalizado para " + totalSaved + " registros.");
    }
}