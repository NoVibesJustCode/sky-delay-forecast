package es.ulpgc.dacd.skydelay.weather.control;


import es.ulpgc.dacd.skydelay.weather.model.Airport;
import es.ulpgc.dacd.skydelay.weather.model.Weather;
import io.github.cdimascio.dotenv.Dotenv;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;


public class OpenWeatherMapFeeder implements WeatherFeeder {
    private final String apiKey;
    private final HttpClient client;
    private final WeatherParser parser;

    public OpenWeatherMapFeeder(WeatherParser parser) {
        this.apiKey = Dotenv.load().get("WEATHER_API_KEY");
        this.client = HttpClient.newHttpClient();
        this.parser = parser;
    }


    @Override
    public List<Weather> fetch(Airport airport) {
        try {
            String json = fetchRawJson(airport.lat(), airport.lon());

            Weather weather = parser.parse(json, airport.icao(), airport.name());

            return List.of(weather);

        } catch (Exception e) {
            System.err.println("Error en el aeropuerto " + airport.icao() + ": " + e.getMessage());
            return null;
        }
    }

    private String fetchRawJson(double lat, double lon) throws Exception {
        String url = String.format(
                "https://api.openweathermap.org/data/2.5/weather?lat=%f&lon=%f&units=metric&lang=es&appid=%s",
                lat, lon, apiKey
        );

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Error: " + response.statusCode() + " - " + response.body());
        }
        return response.body();
    }
}