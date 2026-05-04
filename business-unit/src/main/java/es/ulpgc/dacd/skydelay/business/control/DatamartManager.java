package es.ulpgc.dacd.skydelay.business.control;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.*;
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

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_flights_origin ON flights(origin_icao);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_flights_destination ON flights(destination_icao);");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_features_origin ON flight_features(origin_icao);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_features_destination ON flight_features(destination_icao);");

            logger.info("Datamart initialized with multi-target prediction support.");

        } catch (SQLException e) {
            logger.error("Failed to initialize Datamart tables: {}", e.getMessage(), e);
        }
    }

    public void saveWeather(Weather w) {
        String sql = """
        INSERT OR REPLACE INTO current_weather 
        (airport_icao, description, temperature, feels_like, humidity, visibility, wind_speed, wind_gust, cloudiness, timestamp)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, w.icao());
            pstmt.setString(2, w.description());
            pstmt.setDouble(3, w.temp());
            pstmt.setDouble(4, w.feelsLike());
            pstmt.setInt(5, w.humidity());
            pstmt.setInt(6, w.visibility());
            pstmt.setDouble(7, w.windSpeed());
            pstmt.setDouble(8, w.windGust());
            pstmt.setInt(9, w.cloudsPct());
            pstmt.setString(10, w.ts().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error saving weather: {}", e.getMessage());
        }
    }

    public void saveFlightAndFeatures(Flight f) {
        String sqlFlight = """
        INSERT OR REPLACE INTO flights 
        (flight_id, origin_icao, destination_icao, date, status, departure_delay, arrival_delay, distance_km, scheduled_departure_time, scheduled_arrival_time, aircraft_model, timestamp)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;

        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sqlFlight)) {
                pstmt.setString(1, f.flightId());
                pstmt.setString(2, f.origin());
                pstmt.setString(3, f.destination());
                pstmt.setString(4, f.date());
                pstmt.setString(5, f.status());
                pstmt.setInt(6, f.departureDelay());
                pstmt.setInt(7, f.arrivalDelay());
                pstmt.setInt(8, f.distanceKm());
                pstmt.setString(9, f.departureTimeUTC());
                pstmt.setString(10, f.arrivalTimeUTC());
                pstmt.setString(11, f.aircraftModel());
                pstmt.setString(12, f.ts().toString());
                pstmt.executeUpdate();
            }

            processFlightFeatures(conn, f);
            conn.commit();
        } catch (SQLException e) {
            logger.error("Error in flight transaction: {}", e.getMessage());
        }
    }

    private void processFlightFeatures(Connection conn, Flight f) throws SQLException {
        String selectWeatherSql = "SELECT * FROM current_weather WHERE airport_icao = ?";

        Double temp = null, windSpeed = null, windGust = null;
        Integer visibility = null, clouds = null;

        try (PreparedStatement pstmtWeather = conn.prepareStatement(selectWeatherSql)) {
            pstmtWeather.setString(1, translator.toIcao(f.origin()));
            try (ResultSet rs = pstmtWeather.executeQuery()) {
                if (rs.next()) {
                    temp = rs.getDouble("temperature");
                    visibility = rs.getInt("visibility");
                    windSpeed = rs.getDouble("wind_speed");
                    windGust = rs.getDouble("wind_gust");
                    clouds = rs.getInt("cloudiness");
                }
            }
        }

        String insertFeaturesSql = """
        INSERT INTO flight_features (
            flight_id, origin_icao, destination_icao, distance_km, 
            departure_delay, arrival_delay, dep_delay_category, arr_delay_category,
            weather_temp, weather_visibility, weather_wind_speed, weather_wind_gust, weather_clouds
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;

        try (PreparedStatement pstmtFeat = conn.prepareStatement(insertFeaturesSql)) {
            pstmtFeat.setString(1, f.flightId());
            pstmtFeat.setString(2, translator.toIcao(f.origin()));
            pstmtFeat.setString(3, translator.toIcao(f.destination()));
            pstmtFeat.setInt(4, f.distanceKm());
            pstmtFeat.setInt(5, f.departureDelay());
            pstmtFeat.setInt(6, f.arrivalDelay());
            pstmtFeat.setString(7, f.departureDelay() > 15 ? "DELAYED" : "ON_TIME");
            pstmtFeat.setString(8, f.arrivalDelay() > 15 ? "DELAYED" : "ON_TIME");
            if (temp != null) pstmtFeat.setDouble(9, temp); else pstmtFeat.setNull(9, Types.REAL);
            if (visibility != null) pstmtFeat.setInt(10, visibility); else pstmtFeat.setNull(10, Types.INTEGER);
            if (windSpeed != null) pstmtFeat.setDouble(11, windSpeed); else pstmtFeat.setNull(11, Types.REAL);
            if (windGust != null) pstmtFeat.setDouble(12, windGust); else pstmtFeat.setNull(12, Types.REAL);
            if (clouds != null) pstmtFeat.setInt(13, clouds); else pstmtFeat.setNull(13, Types.INTEGER);
            pstmtFeat.executeUpdate();
        }

        logger.debug("Features processed for flight {} towards {}", f.flightId(), f.destination());
    }
}