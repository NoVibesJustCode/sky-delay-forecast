package es.ulpgc.dacd.skydelay.weather;

import es.ulpgc.dacd.skydelay.weather.control.*;
import es.ulpgc.dacd.skydelay.weather.model.Weather;

import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        AirportsReader reader = new AirportsReader();
        WeatherParser parser = new WeatherParser();
        WeatherFeeder feeder = new OpenWeatherMapFeeder(reader, parser);
        WeatherStore store = new SqliteWeatherStore("storage/db/weather.db");
        Control control = new Control(feeder, store);

        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        System.out.println("OpenWeatherMap feeder CLI");

        while (running) {
            System.out.println("\nSeleccione una opción:");
            System.out.println("1. Obtener datos meteorológicos actuales de todos los aeropuertos españoles");
            System.out.println("2. Almacenar datos en la base de datos");
            System.out.println("3. Consultar información meteorológica de un aeropuerto específico");
            System.out.println("0. Salir");
            System.out.print("> ");

            String option = scanner.nextLine();

            switch (option) {
                case "1":
                    System.out.println("Consultando API...");
                    feeder.fetch().forEach(w -> System.out.println(w.toString()));
                    break;

                case "2":
                    System.out.println("Iniciando proceso de persistencia...");
                    control.execute();
                    break;

                case "3":
                    System.out.print("Introduzca el código ICAO del aeropuerto (ej. GCLP): ");
                    String icaoInput = scanner.nextLine().toUpperCase().trim();
                    searchForIcao(feeder, icaoInput);
                    break;

                case "0":
                    running = false;
                    System.out.println("Cerrando aplicación...");
                    break;

                default:
                    System.out.println("Opción no válida.");
            }
        }
        scanner.close();
    }

    private static void searchForIcao(WeatherFeeder feeder, String icao) {
        List<Weather> weathers = feeder.fetch();
        weathers.stream()
                .filter(w -> w.icao().equalsIgnoreCase(icao))
                .findFirst()
                .ifPresentOrElse(
                        w -> System.out.println("Datos encontrados: " + w),
                        () -> System.out.println("No se encontraron datos para el código: " + icao)
                );
    }
}