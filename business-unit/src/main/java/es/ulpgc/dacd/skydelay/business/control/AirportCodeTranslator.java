package es.ulpgc.dacd.skydelay.business.control;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AirportCodeTranslator {
    private final Map<String, String> icaoToIata = new HashMap<>();
    private final Map<String, String> iataToIcao = new HashMap<>();

    public AirportCodeTranslator(String csvPath) {
        loadMappings(csvPath);
    }

    private void loadMappings(String csvPath) {
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                String[] cols = line.split(",");
                if (cols.length >= 2) {
                    String icao = cols[0].trim();
                    String iata = cols[1].trim();
                    icaoToIata.put(icao, iata);
                    iataToIcao.put(iata, icao);
                }
            }
        } catch (IOException e) {
            System.err.println("Error cargando traductor de códigos: " + e.getMessage());
        }
    }

    public String toIata(String icao) {
        return icaoToIata.getOrDefault(icao, icao);
    }

    public String toIcao(String iata) {
        return iataToIcao.getOrDefault(iata, iata);
    }
}