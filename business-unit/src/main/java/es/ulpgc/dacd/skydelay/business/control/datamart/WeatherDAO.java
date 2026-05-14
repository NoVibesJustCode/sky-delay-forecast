package es.ulpgc.dacd.skydelay.business.control.datamart;

import es.ulpgc.dacd.skydelay.weather.model.Weather;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.*;

public class WeatherDAO {
    private static final Logger logger = LoggerFactory.getLogger(WeatherDAO.class);
    private final DatamartManager db;

    public WeatherDAO(DatamartManager db) { this.db = db; }

    public void save(Weather w) {
        String sql = "INSERT OR REPLACE INTO weather_records VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, w.icao());
            pstmt.setDouble(2, w.temp());
            pstmt.setDouble(3, w.windSpeed());
            pstmt.setDouble(4, w.windGust());
            pstmt.setInt(5, w.visibility());
            pstmt.setString(6, w.ts().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error saving weather: {}", e.getMessage());
        }
    }

    public Weather findClosest(String icao, String isoTime) {
        String sql = "SELECT * FROM weather_records WHERE airport_icao = ? " +
                     "ORDER BY abs(julianday(timestamp) - julianday(?)) ASC LIMIT 1";
        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, icao);
            pstmt.setString(2, isoTime);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Weather(
                        Instant.parse(rs.getString("timestamp")), "", rs.getString("airport_icao"), "", "",
                        rs.getDouble("temp"), 0, 0,
                        rs.getInt("visibility"),
                        rs.getDouble("wind_speed"),
                        rs.getDouble("wind_gust"),
                        0
                );
            }
        } catch (SQLException e) {
            logger.error("findClosest weather error: {}", e.getMessage());
        }
        return null;
    }

    public List<Map<String, Object>> getSeries(String icao, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        String sql = "SELECT airport_icao, temp, wind_speed, wind_gust, visibility, timestamp " +
                     "FROM weather_records WHERE airport_icao = ? ORDER BY timestamp DESC LIMIT ?";
        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, icao);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("icao",       rs.getString("airport_icao"));
                row.put("temp",       rs.getDouble("temp"));
                row.put("windSpeed",  rs.getDouble("wind_speed"));
                row.put("windGust",   rs.getDouble("wind_gust"));
                row.put("visibility", rs.getInt("visibility"));
                row.put("timestamp",  rs.getString("timestamp"));
                results.add(row);
            }
        } catch (SQLException e) {
            logger.error("getSeries error: {}", e.getMessage());
        }
        return results;
    }

    public List<Map<String, Object>> getAllWeatherRecords(int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        String sql = "SELECT airport_icao, temp, wind_speed, wind_gust, visibility, timestamp " +
                     "FROM weather_records ORDER BY timestamp DESC LIMIT ?";
        try (Connection conn = db.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("icao",       rs.getString("airport_icao"));
                row.put("temp",       rs.getDouble("temp"));
                row.put("windSpeed",  rs.getDouble("wind_speed"));
                row.put("windGust",   rs.getDouble("wind_gust"));
                row.put("visibility", rs.getInt("visibility"));
                row.put("timestamp",  rs.getString("timestamp"));
                results.add(row);
            }
        } catch (SQLException e) {
            logger.error("getAllWeatherRecords error: {}", e.getMessage());
        }
        return results;
    }
}