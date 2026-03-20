package es.ulpgc.dacd.skydelay.weather;


public class Main {
    public static void main(String[] args) {
        es.ulpgc.dacd.skydelay.weather.WeatherService service = new es.ulpgc.dacd.skydelay.weather.WeatherService();

        // Coordenadas del aeropuerto de Madrid
        double lat = 40.4839;
        double lon = -3.5680;

        String response = service.getCurrentWeather(lat, lon);

        System.out.println(response);
    }
}

