package es.ulpgc.dacd.skydelay.weather;

import es.ulpgc.dacd.skydelay.weather.control.AirportReader;
import es.ulpgc.dacd.skydelay.weather.control.WeatherParser;
import es.ulpgc.dacd.skydelay.weather.control.WeatherService;
import es.ulpgc.dacd.skydelay.weather.model.WeatherData;

public class Main {
    public static void main(String[] args) {


        WeatherService service = new WeatherService();

        AirportReader airportReader = new AirportReader();

        double lat = airportReader.getCoorditates("LEMD")[0];
        double lon = airportReader.getCoorditates("LEMD")[1];

        try {
            String json = service.fetchRawJson(lat, lon);

            WeatherData data = WeatherParser.parseJson(json);

            System.out.println(data);

        } catch (Exception e) {
            System.err.println("Ocurrió un error: " + e.getMessage());
        }
    }
}