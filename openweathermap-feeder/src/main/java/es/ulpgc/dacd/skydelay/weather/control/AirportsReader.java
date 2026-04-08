package es.ulpgc.dacd.skydelay.weather.control;

import es.ulpgc.dacd.skydelay.weather.model.Airport;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AirportsReader {
    private final String csvPath;
    private final int COL_ICAO = 0;
    private final int COL_NAME = 2;
    private final int COL_LAT = 4;
    private final int COL_LON = 5;

    public AirportsReader(String csvPath) {
        this.csvPath = csvPath;
    }

    public List<Airport> read() {
        List<Airport> airports = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String line;

            while ((line = br.readLine()) != null) {
                String[] cols = line.split(",");
                if (cols.length > Math.max(COL_LAT, COL_LON)) {
                    try {
                        String icao = cols[COL_ICAO].trim();
                        String name = cols[COL_NAME].trim();
                        double lat = Double.parseDouble(cols[COL_LAT].trim());
                        double lon = Double.parseDouble(cols[COL_LON].trim());

                        airports.add(new Airport(icao, name, lat, lon));
                    } catch (NumberFormatException e) {
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error de lectura: " + e.getMessage());
        }
        return airports;
    }
}