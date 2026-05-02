package es.ulpgc.dacd.skydelay.business.control;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatamartManager {
    private static final Logger logger = LoggerFactory.getLogger(DatamartManager.class);
    private final String dbUrl;

    public DatamartManager(String dbPath) {
        this.dbUrl = "jdbc:sqlite:" + dbPath;
    }

    public void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {

            // 1. Clima actual: Base para predicciones en tiempo real
            String createWeatherTable = """
                CREATE TABLE IF NOT EXISTS current_weather (
                    airport_icao TEXT PRIMARY KEY,
                    description TEXT,
                    temperature REAL,
                    feels_like REAL,
                    humidity INTEGER,
                    visibility INTEGER,
                    wind_speed REAL,
                    wind_gust REAL,
                    cloudiness INTEGER,
                    timestamp DATETIME
                );
                """;
            stmt.execute(createWeatherTable);

            String createFlightsTable = """
                CREATE TABLE IF NOT EXISTS flights (
                    flight_id TEXT PRIMARY KEY,
                    origin_icao TEXT,
                    destination_icao TEXT,
                    date TEXT,
                    status TEXT,
                    departure_delay INTEGER,
                    arrival_delay INTEGER,
                    distance_km INTEGER,
                    scheduled_departure_time TEXT,
                    scheduled_arrival_time TEXT,
                    aircraft_model TEXT,
                    timestamp DATETIME
                );
                """;
            stmt.execute(createFlightsTable);

            String createFeaturesTable = """
                CREATE TABLE IF NOT EXISTS flight_features (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    flight_id TEXT,
                    origin_icao TEXT,
                    destination_icao TEXT,
                    distance_km INTEGER,
                    departure_delay INTEGER,
                    arrival_delay INTEGER,
                    dep_delay_category TEXT, 
                    arr_delay_category TEXT,
                    weather_temp REAL,
                    weather_visibility INTEGER,
                    weather_wind_speed REAL,
                    weather_wind_gust REAL,
                    weather_clouds INTEGER,
                    recorded_at DATETIME DEFAULT CURRENT_TIMESTAMP
                );
                """;
            stmt.execute(createFeaturesTable);

            logger.info("Datamart initialized with multi-target prediction support.");

        } catch (SQLException e) {
            logger.error("Failed to initialize Datamart tables: {}", e.getMessage(), e);
        }
    }
}