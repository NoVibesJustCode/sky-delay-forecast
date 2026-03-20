package es.ulpgc.dacd.skydelay.weather;

import io.github.cdimascio.dotenv.Dotenv;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class WeatherService {
    private final String apiKey;
    private final HttpClient client;

    public WeatherService() {
        this.apiKey = Dotenv.configure().directory("/").load().get("WEATHER_API_KEY");
        this.client = HttpClient.newHttpClient();
    }

    public String fetchRawJson(double lat, double lon) throws Exception {
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