package es.ulpgc.dacd.skydelay.business.control.services;

import es.ulpgc.dacd.skydelay.business.control.datamart.FlightHistoricalDAO;
import es.ulpgc.dacd.skydelay.business.control.datamart.FlightPredictionsDAO;
import es.ulpgc.dacd.skydelay.business.control.datamart.WeatherDAO;

import java.util.List;
import java.util.Map;

public class DataStore {

    private final FlightHistoricalDAO historicalDAO;
    private final FlightPredictionsDAO predictionsDAO;
    private final WeatherDAO weatherDAO;

    public DataStore(FlightHistoricalDAO historicalDAO, FlightPredictionsDAO predictionsDAO,
                     WeatherDAO weatherDAO) {
        this.historicalDAO  = historicalDAO;
        this.predictionsDAO = predictionsDAO;
        this.weatherDAO     = weatherDAO;
    }

    public double getAverageDelayForAirport(String icao, int limit) {
        return historicalDAO.getAverageDelay(icao, limit);
    }

    public List<Map<String, String>> fetchHistoricalFlights(String icao, int limit) {
        return historicalDAO.getHistoricalAirportFlights(icao, limit);
    }

    public List<Map<String, Object>> fetchRichHistoricalFlights(String icao, int limit) {
        return historicalDAO.getRichHistoricalFlights(icao, limit);
    }

    public List<Map<String, String>> fetchRecentPredictions(String icao, int limit) {
        return predictionsDAO.getRecentAirportPredictions(icao, limit);
    }

    public List<Map<String, Object>> fetchWeatherSeries(String icao, int limit) {
        return weatherDAO.getSeries(icao, limit);
    }

    public Map<String, Double> fetchDelayRates(int recentLimit) {
        return historicalDAO.getDelayRateByAirport(recentLimit);
    }
}
