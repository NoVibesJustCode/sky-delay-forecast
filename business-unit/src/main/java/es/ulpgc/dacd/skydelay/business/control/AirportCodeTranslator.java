package es.ulpgc.dacd.skydelay.business.control;

import es.ulpgc.dacd.skydelay.business.model.AirportData;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Collection;

public class AirportCodeTranslator {
    private final Map<String, String> icaoToIata = new HashMap<>();
    private final Map<String, String> iataToIcao = new HashMap<>();
    private final Map<String, AirportData> airportDataMap = new HashMap<>();

    public AirportCodeTranslator(String csvPath) {
        loadMappings(csvPath);
    }

    private void loadMappings(String csvPath) {
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                String[] cols = line.split(",");
                if (cols.length >= 6) {
                    String icao = cols[0].trim();
                    String iata = cols[1].trim();
                    String name = cols[2].trim();
                    try {
                        double lat = Double.parseDouble(cols[4].trim());
                        double lon = Double.parseDouble(cols[5].trim());
                        icaoToIata.put(icao, iata);
                        iataToIcao.put(iata, icao);
                        airportDataMap.put(icao, new AirportData(icao, iata, name, lat, lon));
                    } catch (NumberFormatException ignored) {}
                } else if (cols.length >= 2) {
                    String icao = cols[0].trim();
                    String iata = cols[1].trim();
                    icaoToIata.put(icao, iata);
                    iataToIcao.put(iata, icao);
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading airport code translator: " + e.getMessage());
        }
    }

    public String toIata(String icao) {
        return icaoToIata.getOrDefault(icao, icao);
    }

    public String toIcao(String iata) {
        return iataToIcao.getOrDefault(iata, iata);
    }

    public Collection<AirportData> getAirports() {
        return airportDataMap.values();
    }
}