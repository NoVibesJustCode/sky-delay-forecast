package es.ulpgc.dacd.skydelay.business.control.services;

import es.ulpgc.dacd.skydelay.business.control.datamart.FlightDAO;
import es.ulpgc.dacd.skydelay.business.control.datamart.WeatherDAO;

import java.util.List;
import java.util.Map;

public class DataStore {

    private final FlightDAO flightDAO;
    private final WeatherDAO weatherDAO;

    public DataStore(FlightDAO flightDAO, WeatherDAO weatherDAO) {
        this.flightDAO = flightDAO;
        this.weatherDAO = weatherDAO;
    }


    public double getAverageDelayForAirport(String icao, int limit) {
        return flightDAO.getAverageDelay(icao, limit);
    }


    public List<Map<String, String>> fetchHistoricalFlights(String icao, int limit) {
        return flightDAO.getHistoricalAirportFlights(icao, limit);
    }

    public List<Map<String, Object>> fetchRichHistoricalFlights(String icao, int limit) {
        return flightDAO.getRichHistoricalFlights(icao, limit);
    }

    public List<Map<String, String>> fetchRecentPredictions(String icao, int limit) {
        return flightDAO.getRecentAirportPredictions(icao, limit);
    }

    public List<Map<String, Object>> fetchWeatherSeries(String icao, int limit) {
        return weatherDAO.getSeries(icao, limit);
    }
}
