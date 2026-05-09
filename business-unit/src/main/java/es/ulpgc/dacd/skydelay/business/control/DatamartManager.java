package es.ulpgc.dacd.skydelay.business.control;

import es.ulpgc.dacd.skydelay.business.model.FlightFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.*;
import java.time.Instant;
import java.util.*;

import es.ulpgc.dacd.skydelay.weather.model.Weather;
import es.ulpgc.dacd.skydelay.flights.model.Flight;


public class DatamartManager {
    private static final Logger logger = LoggerFactory.getLogger(DatamartManager.class);
    private final String dbUrl;
    private final AirportCodeTranslator translator;

    public DatamartManager(String dbPath, String csvPath) {
        this.dbUrl = "jdbc:sqlite:" + dbPath;
        this.translator = new AirportCodeTranslator(csvPath);
    }

    public void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS weather_records (
                    airport_icao TEXT,
                    temperature REAL,
                    wind_speed REAL,
                    wind_gust REAL,  -- NUEVO
                    visibility INTEGER,
                    timestamp DATETIME,
                    PRIMARY KEY (airport_icao, timestamp)
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS flight_features (
                    flight_id TEXT PRIMARY KEY,
                    temp REAL, wind REAL, gust REAL, vis REAL, -- NUEVO: gust
                    distance_km INTEGER,
                    departure_delay INTEGER, 
                    delay_category TEXT
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS flight_predictions (
                    flight_id TEXT PRIMARY KEY,
                    origin_icao TEXT,
                    dest_icao TEXT,
                    scheduled_time DATETIME,
                    predicted_category TEXT,
                    last_updated DATETIME
                );
            """);

            logger.info("Datamart 'Ready-to-Eat' initialized.");
        } catch (SQLException e) {
            logger.error("DB Initialization error: {}", e.getMessage());
        }
    }

    public void saveWeather(Weather w) {
        String sql = "INSERT OR REPLACE INTO weather_records VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, w.icao());
            pstmt.setDouble(2, w.temp());
            pstmt.setDouble(3, w.windSpeed());
            pstmt.setDouble(4, w.windGust()); // NUEVO
            pstmt.setInt(5, w.visibility());
            pstmt.setString(6, w.ts().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error saving weather: {}", e.getMessage());
        }
    }

    public void saveHistoricalFlight(Flight f) {
        Weather w = fetchClosestWeather(f.origin(), f.departureTimeUTC());
        if (w == null) return;

        String sql = "INSERT OR REPLACE INTO flight_features VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, f.flightId());
            pstmt.setDouble(2, w.temp());
            pstmt.setDouble(3, w.windSpeed());
            pstmt.setDouble(4, w.windGust()); // NUEVO: Sacamos la racha del objeto weather
            pstmt.setDouble(5, w.visibility());
            pstmt.setInt(6, f.distanceKm());
            pstmt.setInt(7, f.departureDelay());
            pstmt.setString(8, categorize(f.departureDelay())); // Categoría basada en salida

            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error saving departure feature: {}", e.getMessage());
        }
    }

    public void saveReadyToEatPrediction(Flight f, String predictedCategory) {
        String sql = "INSERT OR REPLACE INTO flight_predictions VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, f.flightId());
            pstmt.setString(2, f.origin());
            pstmt.setString(3, f.destination());
            pstmt.setString(4, f.departureTimeUTC());
            pstmt.setString(5, predictedCategory);
            pstmt.setString(6, Instant.now().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error saving prediction: {}", e.getMessage());
        }
    }


    public Weather fetchClosestWeather(String icao, String isoTime) {
        String icaoReal = translator.toIcao(icao);
        String sql = "SELECT * FROM weather_records WHERE airport_icao = ? ORDER BY abs(julianday(timestamp) - julianday(?)) ASC LIMIT 1";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, icaoReal);
            pstmt.setString(2, isoTime);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Weather(
                        Instant.parse(rs.getString("timestamp")), "", rs.getString("airport_icao"), "", "",
                        rs.getDouble("temperature"), 0, 0,
                        rs.getInt("visibility"),
                        rs.getDouble("wind_speed"),
                        rs.getDouble("wind_gust"), // NUEVO
                        0
                );
            }
        } catch (SQLException e) { logger.error("Fetch weather error: {}", e.getMessage()); }
        return null;
    }

    public List<FlightFeature> loadTrainingData() {
        List<FlightFeature> data = new ArrayList<>();
        String sql = "SELECT temp, wind, gust, vis, distance_km, delay_category FROM flight_features";

        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

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
            logger.error("Error loading training data: {}", e.getMessage());
        }
        return data;
    }

    public List<Map<String, Object>> getReadyToEatMenu() {
        List<Map<String, Object>> results = new ArrayList<>();
        String sql = "SELECT * FROM flight_predictions ORDER BY scheduled_time ASC";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             ResultSet rs = conn.createStatement().executeQuery(sql)) {
            while (rs.next()) {
                results.add(Map.of(
                        "flight", rs.getString("flight_id"),
                        "route", rs.getString("origin_icao") + "->" + rs.getString("dest_icao"),
                        "time", rs.getString("scheduled_time"),
                        "prediction", rs.getString("predicted_category")
                ));
            }
        } catch (SQLException e) { logger.error("API Menu error: {}", e.getMessage()); }
        return results;
    }

    private String categorize(int mins) {
        if (mins <= 15) return "none";
        if (mins <= 30) return "low";
        if (mins <= 60) return "moderate";
        return "severe";
    }
}