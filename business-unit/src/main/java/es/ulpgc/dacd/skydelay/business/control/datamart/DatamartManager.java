package es.ulpgc.dacd.skydelay.business.control.datamart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.*;

public class DatamartManager {
    private static final Logger logger = LoggerFactory.getLogger(DatamartManager.class);
    private final String dbUrl;

    public DatamartManager(String dbPath) {
        this.dbUrl = "jdbc:sqlite:" + dbPath;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    public void initializeDatabase() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS weather_records (
                    airport_icao TEXT,
                    temp REAL, wind_speed REAL, wind_gust REAL,
                    visibility INTEGER, timestamp DATETIME,
                    PRIMARY KEY (airport_icao, timestamp)
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS flight_features (
                    flight_id TEXT PRIMARY KEY,
                    origin_icao TEXT, dest_icao TEXT,
                    temp REAL, wind REAL, gust REAL, vis REAL,
                    distance_km INTEGER, departure_delay INTEGER, 
                    delay_category TEXT
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS flight_predictions (
                    flight_id TEXT PRIMARY KEY,
                    origin_icao TEXT, dest_icao TEXT,
                    scheduled_time DATETIME, predicted_category TEXT,
                    last_updated DATETIME
                );
            """);

            logger.info("Datamart schema initialized successfully.");
        } catch (SQLException e) {
            logger.error("DB Initialization error: {}", e.getMessage());
        }
    }
}