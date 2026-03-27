package es.ulpgc.dacd.skydelay.weather.control;

import es.ulpgc.dacd.skydelay.weather.model.Weather;

import java.util.List;

public class Control {
    private final WeatherFeeder feeder;
    private final WeatherStore store;

    public Control(WeatherFeeder feeder, WeatherStore store) {
        this.feeder = feeder;
        this.store = store;
    }

    public void execute() {
        List<Weather> weathers = feeder.fetch();
        for (Weather weather : weathers) {
            store.save(weather);
        }
    }
}