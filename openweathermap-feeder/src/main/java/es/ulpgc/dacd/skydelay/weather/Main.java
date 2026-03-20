package es.ulpgc.dacd.skydelay.weather;

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