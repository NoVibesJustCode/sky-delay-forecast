package es.ulpgc.dacd.skydelay.business.control.datamart;

import es.ulpgc.dacd.skydelay.business.model.FlightFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * DAO responsible for historical flight data (flight_features table).
 * Handles saving, querying, training data loading, and delay statistics.
 */
public class FlightHistoricalDAO {
    private static final Logger logger = LoggerFactory.getLogger(FlightHistoricalDAO.class);
    private final DatamartManager db;

    public FlightHistoricalDAO(DatamartManager db) { this.db = db; }

    public void saveFeature(String flightId, String origin, String dest,
                            double temp, double wind, double gust, double vis,
                            int dist, int delay, String category,
                            String scheduledDeparture, String aircraftModel) {
        String sql = "INSERT OR REPLACE INTO flight_features VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, flightId);
            pstmt.setString(2, origin);
            pstmt.setString(3, dest);
            pstmt.setDouble(4, temp);
            pstmt.setDouble(5, wind);
            pstmt.setDouble(6, gust);
            pstmt.setDouble(7, vis);
            pstmt.setInt(8, dist);
            pstmt.setInt(9, delay);
            pstmt.setString(10, category);
            pstmt.setString(11, scheduledDeparture);
            pstmt.setString(12, aircraftModel);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error saving flight feature: {}", e.getMessage());
        }
    }

    public List<Map<String, Object>> getAllFlightFeatures() {
        List<Map<String, Object>> results = new ArrayList<>();
        String sql = "SELECT flight_id, origin_icao, dest_icao, temp, wind, gust, vis, " +
                     "distance_km, departure_delay, delay_category, " +
                     "scheduled_departure, aircraft_model FROM flight_features";
        try (Connection conn = db.getConnection();
             ResultSet rs = conn.createStatement().executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("flightId",      rs.getString("flight_id"));
                row.put("originIcao",    rs.getString("origin_icao"));
                row.put("destIcao",      rs.getString("dest_icao"));
                row.put("temp",          rs.getDouble("temp"));
                row.put("wind",          rs.getDouble("wind"));
                row.put("gust",          rs.getDouble("gust"));
                row.put("vis",           rs.getDouble("vis"));
                row.put("distanceKm",    rs.getInt("distance_km"));
                row.put("departureDelay", rs.getInt("departure_delay"));
                row.put("delayCategory", rs.getString("delay_category"));
                row.put("scheduledDeparture", rs.getString("scheduled_departure"));
                row.put("aircraftModel",      rs.getString("aircraft_model"));
                results.add(row);
            }
        } catch (SQLException e) {
            logger.error("getAllFlightFeatures error: {}", e.getMessage());
        }
        return results;
    }

    public List<Map<String, String>> getHistoricalAirportFlights(String icao, int limit) {
        List<Map<String, String>> results = new ArrayList<>();
        String sql = "SELECT flight_id, dest_icao, delay_category " +
                     "FROM flight_features WHERE origin_icao = ? ORDER BY flight_id DESC LIMIT ?";
        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, icao);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String dest = rs.getString("dest_icao") != null ? rs.getString("dest_icao") : "N/A";
                results.add(Map.of(
                        "route",      rs.getString("flight_id") + " → " + dest,
                        "prediction", rs.getString("delay_category")
                ));
            }
        } catch (SQLException e) {
            logger.error("getHistoricalAirportFlights error: {}", e.getMessage());
        }
        return results;
    }

    public List<Map<String, Object>> getRichHistoricalFlights(String icao, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        String sql = "SELECT flight_id, dest_icao, departure_delay, delay_category, " +
                     "temp, wind, gust, vis, distance_km " +
                     "FROM flight_features WHERE origin_icao = ? ORDER BY flight_id DESC LIMIT ?";
        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, icao);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                String flightId = rs.getString("flight_id");
                String dest     = rs.getString("dest_icao") != null ? rs.getString("dest_icao") : "N/A";
                row.put("flightId",    flightId);
                row.put("dest",        dest);
                row.put("route",       flightId + " → " + dest);
                row.put("delay",       rs.getInt("departure_delay"));
                row.put("category",    rs.getString("delay_category"));
                row.put("prediction",  rs.getString("delay_category"));
                row.put("temp",        rs.getDouble("temp"));
                row.put("wind",        rs.getDouble("wind"));
                row.put("gust",        rs.getDouble("gust"));
                row.put("vis",         rs.getDouble("vis"));
                row.put("distanceKm",  rs.getInt("distance_km"));
                results.add(row);
            }
        } catch (SQLException e) {
            logger.error("getRichHistoricalFlights error: {}", e.getMessage());
        }
        return results;
    }

    public List<FlightFeature> loadTrainingData() {
        List<FlightFeature> data = new ArrayList<>();
        String sql = "SELECT temp, wind, gust, vis, distance_km, delay_category FROM flight_features";
        try (Connection conn = db.getConnection();
             ResultSet rs = conn.createStatement().executeQuery(sql)) {
            while (rs.next()) {
                data.add(new FlightFeature(
                        rs.getDouble("temp"),
                        rs.getDouble("wind"),
                        rs.getDouble("gust"),
                        rs.getDouble("vis"),
                        rs.getInt("distance_km"),
                        rs.getString("delay_category")
                ));
            }
        } catch (SQLException e) {
            logger.error("loadTrainingData error: {}", e.getMessage());
        }
        return data;
    }

    public double getAverageDelay(String icao, int limit) {
        String sql = "SELECT AVG(departure_delay) as avg_delay FROM " +
                     "(SELECT departure_delay FROM flight_features " +
                     "WHERE origin_icao = ? ORDER BY flight_id DESC LIMIT ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, icao);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getDouble("avg_delay");
        } catch (SQLException e) {
            logger.error("getAverageDelay error: {}", e.getMessage());
        }
        return 0.0;
    }

    public Map<String, Double> getDelayRateByAirport(int recentLimit) {
        Map<String, Double> result = new LinkedHashMap<>();
        String airportsSql = "SELECT DISTINCT origin_icao FROM flight_features";
        String rateSql = "SELECT COUNT(*) as total, " +
                         "SUM(CASE WHEN departure_delay > 15 THEN 1 ELSE 0 END) as delayed " +
                         "FROM (SELECT departure_delay FROM flight_features " +
                         "WHERE origin_icao = ? ORDER BY rowid DESC LIMIT ?)";
        try (Connection conn = db.getConnection();
             Statement airStmt = conn.createStatement();
             ResultSet airRs = airStmt.executeQuery(airportsSql)) {
            while (airRs.next()) {
                String icao = airRs.getString("origin_icao");
                try (PreparedStatement pstmt = conn.prepareStatement(rateSql)) {
                    pstmt.setString(1, icao);
                    pstmt.setInt(2, recentLimit);
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) {
                        int total = rs.getInt("total");
                        int delayed = rs.getInt("delayed");
                        double rate = total > 0 ? (double) delayed / total : 0.0;
                        result.put(icao, Math.round(rate * 1000.0) / 1000.0);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("getDelayRateByAirport error: {}", e.getMessage());
        }
        return result;
    }

    public static String categorize(int mins) {
        if (mins <= 15) return "none";
        if (mins <= 30) return "low";
        if (mins <= 60) return "moderate";
        return "severe";
    }
}
