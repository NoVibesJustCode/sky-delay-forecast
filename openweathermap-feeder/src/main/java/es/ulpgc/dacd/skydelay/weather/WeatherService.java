package es.ulpgc.dacd.skydelay.weather;

import io.github.cdimascio.dotenv.Dotenv;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class WeatherService {

    private final String apiKey = Dotenv.configure().directory("/").load().get("WEATHER_API_KEY");

    private final HttpClient client = HttpClient.newHttpClient();

    public String getCurrentWeather(double lat, double lon) {

        String url = String.format(
                "https://api.openweathermap.org/data/2.5/weather?lat=%f&lon=%f&units=metric&lang=es&appid=%s",
                lat, lon, apiKey
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
