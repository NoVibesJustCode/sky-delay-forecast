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

    public List<Map<String, String>> getHistoricalFlightsForAirport(String icao) {
        return dataStore.fetchHistoricalFlights(icao, 5);
    }

    public List<Map<String, Object>> getAirportWeatherHistory(String icao, int limit) {
        return dataStore.fetchWeatherSeries(icao, limit);
    }
}