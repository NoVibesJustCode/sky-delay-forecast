package es.ulpgc.dacd.skydelay.business.control.services;

import es.ulpgc.dacd.skydelay.business.control.AirportCodeTranslator;
import es.ulpgc.dacd.skydelay.business.model.AirportData;
import java.util.*;

public class MapDataService {
    private final DataStore dataStore;
    private final AirportCodeTranslator translator;

    public MapDataService(DataStore dataStore, AirportCodeTranslator translator) {
        this.dataStore = dataStore;
        this.translator = translator;
    }

    public List<Map<String, Object>> getAirportsWithPredictions() {
        List<Map<String, Object>> result = new ArrayList<>();
        Collection<AirportData> airports = translator.getAirports();

        Map<String, Double> delayRates = dataStore.fetchDelayRates(50);

        for (AirportData airport : airports) {
            Map<String, Object> airportMap = new LinkedHashMap<>();
            airportMap.put("icao", airport.icao());
            airportMap.put("iata", airport.iata());
            airportMap.put("name", airport.name());
            airportMap.put("lat",  airport.lat());
            airportMap.put("lng",  airport.lon());

            List<Map<String, String>> predictions = dataStore.fetchRecentPredictions(airport.icao(), 5);
            airportMap.put("predictions", predictions);

            double delayRate = delayRates.getOrDefault(airport.icao(), 0.0);
            airportMap.put("delayRate", delayRate);

            String worstSeverity = deriveWorstSeverity(predictions);
            airportMap.put("severity", worstSeverity);

            result.add(airportMap);
        }
        return result;
    }

    public List<Map<String, Object>> getAirportsWithCurrentDelays() {
        List<Map<String, Object>> result = new ArrayList<>();
        Collection<AirportData> airports = translator.getAirports();

        for (AirportData airport : airports) {
            Map<String, Object> airportMap = new LinkedHashMap<>();
            airportMap.put("icao", airport.icao());
            airportMap.put("iata", airport.iata());
            airportMap.put("name", airport.name());
            airportMap.put("lat",  airport.lat());
            airportMap.put("lng",  airport.lon());

            double avgDelay = dataStore.getAverageDelayForAirport(airport.icao(), 10);
            airportMap.put("delay", avgDelay);

            airportMap.put("recentFlights", dataStore.fetchRichHistoricalFlights(airport.icao(), 12));

            result.add(airportMap);
        }
        return result;
    }

    private static String deriveWorstSeverity(List<Map<String, String>> predictions) {
        int worst = 0;
        for (Map<String, String> p : predictions) {
            String cat = p.getOrDefault("prediction", "none").toLowerCase();
            int level = switch (cat) {
                case "severe"   -> 3;
                case "moderate" -> 2;
                case "low"      -> 1;
                default         -> 0;
            };
            if (level > worst) worst = level;
        }
        return switch (worst) {
            case 3  -> "severe";
            case 2  -> "moderate";
            case 1  -> "low";
            default -> "none";
        };
    }

    public List<Map<String, String>> getHistoricalFlightsForAirport(String icao) {
        return dataStore.fetchHistoricalFlights(icao, 5);
    }

    public List<Map<String, Object>> getAirportWeatherHistory(String icao, int limit) {
        return dataStore.fetchWeatherSeries(icao, limit);
    }
}