package es.ulpgc.dacd.skydelay.weather;

import es.ulpgc.dacd.skydelay.weather.control.WeatherParser;
import es.ulpgc.dacd.skydelay.weather.control.WeatherService;
import es.ulpgc.dacd.skydelay.weather.model.WeatherData;

public class Main {
    public static void main(String[] args) {
        WeatherService service = new WeatherService();

        // Coordenadas de ejemplo aeropuerto Madrid-Barajas
        double lat = 40.4839;
        double lon = -3.5680;

        try {
            String json = service.fetchRawJson(lat, lon);

            WeatherData data = WeatherParser.parseJson(json);

            System.out.println(data);

        } catch (Exception e) {
            System.err.println("Ocurrió un error: " + e.getMessage());
        }
    }
}