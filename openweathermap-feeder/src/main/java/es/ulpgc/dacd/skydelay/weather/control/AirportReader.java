package es.ulpgc.dacd.skydelay.weather.control;

import es.ulpgc.dacd.skydelay.weather.model.WeatherData;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AirportReader {


    private final int COL_ICAO = 0;
    private final int COL_AIRPORT = 2;
    private final int COL_X = 4;
    private final int COL_Y = 5;


    public double[] getCoorditates(String icaoCode) {
        String[] fila = SearchIcao(icaoCode);

        if (fila != null && fila.length > Math.max(COL_X, COL_Y)) {
            try {
                double x = Double.parseDouble(fila[COL_X]);
                double y = Double.parseDouble(fila[COL_Y]);
                return new double[]{x, y};
            } catch (NumberFormatException e) {
                System.err.println("Error: Las coordenadas de " + icaoCode + " no son números válidos.");
            }
        }
        return null;
    }

    public String getName(String icaoCode) {
        String[] fila = SearchIcao(icaoCode);

        if (fila != null && fila.length > COL_AIRPORT) {
            return fila[COL_AIRPORT].trim();
        }
        return null;
    }


    private String[] SearchIcao(String icaoCode) {
        try (BufferedReader br = new BufferedReader(new FileReader("storage/references/airports.csv"))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] columnas = linea.split(",");

                if (columnas.length > COL_ICAO && columnas[COL_ICAO].trim().equalsIgnoreCase(icaoCode)) {
                    return columnas;
                }
            }
        } catch (IOException e) {
            System.err.println("Error de lectura: " + e.getMessage());
        }
        return null;
    }


}
