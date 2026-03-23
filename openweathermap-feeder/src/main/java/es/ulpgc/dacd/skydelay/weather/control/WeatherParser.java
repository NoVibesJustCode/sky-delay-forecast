package es.ulpgc.dacd.skydelay.weather.control;

import es.ulpgc.dacd.skydelay.weather.model.WeatherData;
import org.json.JSONObject;

public class WeatherParser {
    public static WeatherData parseJson(String jsonRaw) {
        JSONObject root = new JSONObject(jsonRaw);
        JSONObject main = root.getJSONObject("main");
        JSONObject wind = root.getJSONObject("wind");
        JSONObject clouds = root.getJSONObject("clouds");

        return new WeatherData(
                root.getString("name"),
                root.getJSONArray("weather").getJSONObject(0).getString("description"),
                main.getDouble("temp"),
                main.getDouble("feels_like"),
                main.getInt("humidity"),
                root.optInt("visibility"),
                wind.getDouble("speed"),
                wind.optDouble("gust", 0.0),
                clouds.getInt("all"),
                root.getLong("dt")
        );
    }
}