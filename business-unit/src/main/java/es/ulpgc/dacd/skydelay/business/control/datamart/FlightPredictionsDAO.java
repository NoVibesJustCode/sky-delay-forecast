package es.ulpgc.dacd.skydelay.business.control.datamart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public class FlightPredictionsDAO {
    private static final Logger logger = LoggerFactory.getLogger(FlightPredictionsDAO.class);
    private final DatamartManager db;

    public FlightPredictionsDAO(DatamartManager db) { this.db = db; }

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
        String sql = "SELECT flight_id, dest_icao, scheduled_time, predicted_category, last_updated " +
                     "FROM flight_predictions WHERE origin_icao = ? " +
                     "ORDER BY ABS(julianday(scheduled_time) - julianday('now')) ASC LIMIT ?";
        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, icao);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("flight",      rs.getString("flight_id"));
                row.put("dest",        rs.getString("dest_icao"));
                row.put("time",        rs.getString("scheduled_time"));
                row.put("prediction",  rs.getString("predicted_category"));
                row.put("lastUpdated", rs.getString("last_updated"));
                results.add(row);
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
                     "(flight_id IN (SELECT flight_id FROM flight_features) " +
                     " AND julianday('now') > julianday(scheduled_time)) " +
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
}
