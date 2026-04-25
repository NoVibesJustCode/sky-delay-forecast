package es.ulpgc.dacd.skydelay.weather.control;

import es.ulpgc.dacd.skydelay.weather.model.Airport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class AirportsReader {
    private final String csvPath;
    private static final int COL_ICAO = 0;
    private static final int COL_NAME = 2;
    private static final int COL_LAT = 4;
    private static final int COL_LON = 5;

    public AirportsReader(String csvPath) {
        this.csvPath = csvPath;
    }

    public List<Airport> read() {
        try (Stream<String> lines = Files.lines(Paths.get(csvPath))) {
            return lines
                    .map(this::lineToAirport)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (IOException e) {
            System.err.println("Read error: " + e.getMessage());
            return List.of();
        }
    }

    private Optional<Airport> lineToAirport(String line) {
        try {
            String[] cols = line.split(",");
            if (cols.length <= Math.max(COL_LAT, COL_LON)) return Optional.empty();

            return Optional.of(new Airport(
                    cols[COL_ICAO].trim(),
                    cols[COL_NAME].trim(),
                    Double.parseDouble(cols[COL_LAT].trim()),
                    Double.parseDouble(cols[COL_LON].trim())
            ));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}