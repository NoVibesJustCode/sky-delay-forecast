package es.ulpgc.dacd.skydelay.business.control;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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
                    dest_icao TEXT,
                    dep_temp REAL, dep_feels_like REAL, dep_humidity INTEGER,
                    dep_visibility INTEGER, dep_wind_speed REAL, dep_wind_gust REAL, dep_clouds INTEGER,
                    arr_temp REAL, arr_feels_like REAL, arr_humidity INTEGER,
                    arr_visibility INTEGER, arr_wind_speed REAL, arr_wind_gust REAL, arr_clouds INTEGER,
                    distance_km INTEGER,
                    departure_delay INTEGER,
                    arrival_delay INTEGER,
                    delay_category TEXT,
                    recorded_at DATETIME DEFAULT CURRENT_TIMESTAMP
                            );
                """;
            stmt.execute(createFeaturesTable);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_flights_origin ON flights(origin_icao);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_flights_destination ON flights(destination_icao);");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_features_origin ON flight_features(origin_icao);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_features_destination ON flight_features(dest_icao);");

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
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
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
        String schedDep = formatToIso(f.date(), f.departureTimeUTC());
        String schedArr = formatToIso(f.date(), f.arrivalTimeUTC());

        Weather depW = fetchClosestWeather(conn, translator.toIcao(f.origin()), schedDep);
        Weather arrW = fetchClosestWeather(conn, translator.toIcao(f.destination()), schedArr);

        String insertFeaturesSql = """
            INSERT OR IGNORE INTO flight_features (
                flight_id, origin_icao, dest_icao,
                dep_temp, dep_feels_like, dep_humidity, dep_visibility, dep_wind_speed, dep_wind_gust, dep_clouds,
                arr_temp, arr_feels_like, arr_humidity, arr_visibility, arr_wind_speed, arr_wind_gust, arr_clouds,
                distance_km, departure_delay, arrival_delay, delay_category
                ) VALUES (?,?,?, ?,?,?,?,?,?,?, ?,?,?,?,?,?,?, ?,?,?,?)
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(insertFeaturesSql)) {
            pstmt.setString(1, f.flightId());
            pstmt.setString(2, translator.toIcao(f.origin()));
            pstmt.setString(3, translator.toIcao(f.destination()));

            mapWeatherRecordToPstmt(pstmt, depW, 4);
            mapWeatherRecordToPstmt(pstmt, arrW, 11);

            pstmt.setInt(18, f.distanceKm());
            pstmt.setInt(19, f.departureDelay());
            pstmt.setInt(20, f.arrivalDelay());
            pstmt.setString(21, categorize(f.arrivalDelay()));

            pstmt.executeUpdate();
        }
    }

    private Weather fetchClosestWeather(Connection conn, String icao, String isoTime) throws SQLException {
        String sql = "SELECT * FROM current_weather WHERE airport_icao = ? " +
                "ORDER BY abs(julianday(timestamp) - julianday(?)) ASC LIMIT 1";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, icao);
            pstmt.setString(2, isoTime);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Weather(
                            Instant.parse(rs.getString("timestamp")),
                            "datamart-source", // ss (sensor source)
                            rs.getString("airport_icao"),
                            "",
                            rs.getString("description"),
                            rs.getDouble("temperature"),
                            rs.getDouble("feels_like"),
                            rs.getInt("humidity"),
                            rs.getInt("visibility"),
                            rs.getDouble("wind_speed"),
                            rs.getDouble("wind_gust"),
                            rs.getInt("cloudiness")
                    );
                }
            }
        }
        return null;
    }

    private void mapWeatherRecordToPstmt(PreparedStatement pstmt, Weather w, int start) throws SQLException {
        if (w != null) {
            pstmt.setDouble(start, w.temp());
            pstmt.setDouble(start + 1, w.feelsLike());
            pstmt.setInt(start + 2, w.humidity());
            pstmt.setInt(start + 3, w.visibility());
            pstmt.setDouble(start + 4, w.windSpeed());
            pstmt.setDouble(start + 5, w.windGust());
            pstmt.setInt(start + 6, w.cloudsPct());
        } else {
            for (int i = 0; i < 7; i++) pstmt.setNull(start + i, Types.REAL);
        }
    }

    private String categorize(int mins) {
        if (mins <= 15) return "none";
        if (mins <= 30) return "low";
        if (mins <= 60) return "moderate";
        return "severe";
    }

    private String formatToIso(String rawDate, String rawTime) {
        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("dd. MMM yyyy", Locale.ENGLISH);
        String datePart = LocalDate.parse(rawDate, inputFormatter).toString();

        return datePart + "T" + rawTime + ":00Z";
    }

}