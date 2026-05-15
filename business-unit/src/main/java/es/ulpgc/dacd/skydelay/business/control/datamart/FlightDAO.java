package es.ulpgc.dacd.skydelay.business.control.datamart;

import es.ulpgc.dacd.skydelay.business.model.FlightFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public class FlightDAO {
    private static final Logger logger = LoggerFactory.getLogger(FlightDAO.class);
    private final DatamartManager db;

    public FlightDAO(DatamartManager db) { this.db = db; }

    public void saveFeature(String flightId, String origin, String dest,
                            double temp, double wind, double gust, double vis,
                            int dist, int delay, String category) {
        String sql = "INSERT OR REPLACE INTO flight_features VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error saving flight feature: {}", e.getMessage());
        }
    }

    public void savePrediction(String flightId, String origin, String dest,
                               String time, String category) {
        String sql = "INSERT OR REPLACE INTO flight_predictions VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, flightId);
            pstmt.setString(2, origin);
            pstmt.setString(3, dest);
            pstmt.setString(4, time);
            pstmt.setString(5, category);
            pstmt.setString(6, java.time.Instant.now().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error saving prediction: {}", e.getMessage());
        }
    }

    public List<Map<String, String>> getRecentByOrigin(String icao, int limit) {
        List<Map<String, String>> results = new ArrayList<>();
        String sql = "SELECT flight_id, dest_icao, departure_delay, delay_category " +
                     "FROM flight_features WHERE origin_icao = ? ORDER BY flight_id DESC LIMIT ?";
        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, icao);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                results.add(Map.of(
                        "flight", rs.getString("flight_id"),
                        "dest",   rs.getString("dest_icao"),
                        "delay",  rs.getString("departure_delay"),
                        "category", rs.getString("delay_category")
                ));
            }
        } catch (SQLException e) {
            logger.error("getRecentByOrigin error: {}", e.getMessage());
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
                row.put("route",       flightId + " → " + dest);    // legacy alias
                row.put("delay",       rs.getInt("departure_delay"));
                row.put("category",    rs.getString("delay_category"));
                row.put("prediction",  rs.getString("delay_category")); // legacy alias
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

    public List<Map<String, Object>> getAllFlightFeatures() {
        List<Map<String, Object>> results = new ArrayList<>();
        String sql = "SELECT flight_id, origin_icao, dest_icao, temp, wind, gust, vis, " +
                     "distance_km, departure_delay, delay_category FROM flight_features";
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
                results.add(row);
            }
        } catch (SQLException e) {
            logger.error("getAllFlightFeatures error: {}", e.getMessage());
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

    public List<Map<String, Object>> getReadyToEatMenu() {
        List<Map<String, Object>> results = new ArrayList<>();
        String sql = "SELECT * FROM flight_predictions ORDER BY scheduled_time ASC";
        try (Connection conn = db.getConnection();
             ResultSet rs = conn.createStatement().executeQuery(sql)) {
            while (rs.next()) {
                results.add(Map.of(
                        "flight",     rs.getString("flight_id"),
                        "route",      rs.getString("origin_icao") + "->" + rs.getString("dest_icao"),
                        "time",       rs.getString("scheduled_time"),
                        "prediction", rs.getString("predicted_category")
                ));
            }
        } catch (SQLException e) {
            logger.error("getReadyToEatMenu error: {}", e.getMessage());
        }
        return results;
    }

    public List<Map<String, String>> getRecentAirportPredictions(String icao, int limit) {
        List<Map<String, String>> results = new ArrayList<>();
        String sql = "SELECT flight_id, dest_icao, scheduled_time, predicted_category " +
                     "FROM flight_predictions WHERE origin_icao = ? " +
                     "ORDER BY ABS(julianday(scheduled_time) - julianday('now')) ASC LIMIT ?";
        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, icao);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                results.add(Map.of(
                        "flight",     rs.getString("flight_id"),
                        "dest",       rs.getString("dest_icao"),
                        "time",       rs.getString("scheduled_time"),
                        "prediction", rs.getString("predicted_category")
                ));
            }
        } catch (SQLException e) {
            logger.error("getRecentAirportPredictions error: {}", e.getMessage());
        }
        return results;
    }

    public List<Map<String, String>> getAllPredictions(String originFilter) {
        List<Map<String, String>> results = new ArrayList<>();
        String sql;
        if (originFilter != null && !originFilter.isEmpty()) {
            sql = "SELECT flight_id, origin_icao, dest_icao, scheduled_time, predicted_category, last_updated " +
                  "FROM flight_predictions WHERE origin_icao = ? ORDER BY scheduled_time ASC";
        } else {
            sql = "SELECT flight_id, origin_icao, dest_icao, scheduled_time, predicted_category, last_updated " +
                  "FROM flight_predictions ORDER BY scheduled_time ASC";
        }
        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (originFilter != null && !originFilter.isEmpty()) {
                pstmt.setString(1, originFilter);
            }
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("flight",      rs.getString("flight_id"));
                row.put("origin",      rs.getString("origin_icao"));
                row.put("dest",        rs.getString("dest_icao"));
                row.put("time",        rs.getString("scheduled_time"));
                row.put("prediction",  rs.getString("predicted_category"));
                row.put("lastUpdated", rs.getString("last_updated"));
                results.add(row);
            }
        } catch (SQLException e) {
            logger.error("getAllPredictions error: {}", e.getMessage());
        }
        return results;
    }

    public int purgeExpiredPredictions() {
        String sql = "DELETE FROM flight_predictions WHERE " +
                     "flight_id IN (SELECT flight_id FROM flight_features) " +
                     "OR julianday('now') - julianday(scheduled_time) > (4.0/24.0)";
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement()) {
            int deleted = stmt.executeUpdate(sql);
            if (deleted > 0) logger.info("Purged {} expired predictions", deleted);
            return deleted;
        } catch (SQLException e) {
            logger.error("purgeExpiredPredictions error: {}", e.getMessage());
        }
        return 0;
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
