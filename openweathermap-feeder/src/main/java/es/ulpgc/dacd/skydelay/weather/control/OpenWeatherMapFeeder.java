package es.ulpgc.dacd.skydelay.weather.control;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import es.ulpgc.dacd.skydelay.weather.model.Airport;
import es.ulpgc.dacd.skydelay.weather.model.Weather;
import es.ulpgc.dacd.skydelay.weather.model.WeatherForecast;
import io.github.cdimascio.dotenv.Dotenv;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.StreamSupport;

public class OpenWeatherMapFeeder implements WeatherFeeder {
    private final String apiKey;
    private final HttpClient client;
    private final WeatherParser parser;
    private static final Logger logger = LoggerFactory.getLogger(OpenWeatherMapFeeder.class);

    private static final String CURRENT_WEATHER_URL = "https://api.openweathermap.org/data/2.5/weather";
    private static final String FORECAST_WEATHER_URL = "https://api.openweathermap.org/data/2.5/forecast";

    public OpenWeatherMapFeeder(WeatherParser parser) {
        this.apiKey = Dotenv.load().get("OPENWEATHERMAP_API_KEY");
        this.client = HttpClient.newHttpClient();
        this.parser = parser;
    }

    @Override
    public List<Object> fetch(Airport airport) {
        List<Object> results = new ArrayList<>();
        logger.info("Starting weather data fetch for airport: {} ({})", airport.icao(), airport.name());        try {
            results.add(fetchCurrentWeather(airport));
            results.addAll(fetchForecasts(airport));
            logger.info("Successfully fetched weather data for airport: {}", airport.icao());            return results;
        } catch (Exception e) {
            logger.error("Failed to process airport {}", airport.icao(), e);
            return null;
        }
    }

    private Weather fetchCurrentWeather(Airport airport) throws Exception {
        String url = String.format("%s?lat=%f&lon=%f&units=metric&lang=es&appid=%s",
                CURRENT_WEATHER_URL, airport.lat(), airport.lon(), apiKey);

        String json = executeRequest(url);
        return parser.parse(json, airport.icao(), airport.name());
    }

    private List<WeatherForecast> fetchForecasts(Airport airport) throws Exception {
        String url = String.format("%s?lat=%f&lon=%f&units=metric&lang=es&appid=%s",
                FORECAST_WEATHER_URL, airport.lat(), airport.lon(), apiKey);

        String json = executeRequest(url);
        JsonArray list = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("list");

        Instant now = Instant.now();

        return StreamSupport.stream(list.spliterator(), false)
                .map(JsonElement::getAsJsonObject)
                .filter(item -> Instant.ofEpochSecond(item.get("dt").getAsLong()).isAfter(now))
                .findFirst()
                .map(item -> parser.parseForecast(item.toString(), airport.icao(), airport.name()))
                .map(List::of)
                .orElseGet(() -> {
                    if (!list.isEmpty()) {
                        return List.of(parser.parseForecast(list.get(0).toString(), airport.icao(), airport.name()));
                    }
                    return List.of();
                });
    }

    private String executeRequest(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("API Error: " + response.statusCode() + " - " + response.body());
        }
        return response.body();
    }
}