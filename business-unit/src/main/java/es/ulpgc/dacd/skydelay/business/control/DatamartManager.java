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

    public Collection<es.ulpgc.dacd.skydelay.business.model.AirportData> getAirports() {
        return translator.getAirports();
    }

    public void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS weather_records (
                    airport_icao TEXT,
                    temp REAL,
                    wind_speed REAL,
                    wind_gust REAL,
                    visibility INTEGER,
                    timestamp DATETIME,
                    PRIMARY KEY (airport_icao, timestamp)
                );
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS flight_features (
                    flight_id TEXT PRIMARY KEY,
                    origin_icao TEXT,
                    dest_icao TEXT,
                    temp REAL, wind REAL, gust REAL, vis REAL,
                    distance_km INTEGER,
                    departure_delay INTEGER, 
                    delay_category TEXT
                );
            """);

            try {
                stmt.execute("ALTER TABLE flight_features ADD COLUMN origin_icao TEXT");
                stmt.execute("ALTER TABLE flight_features ADD COLUMN dest_icao TEXT");
            } catch (SQLException ignored) {
                // Columns might already exist
            }

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
            pstmt.setDouble(4, w.windGust());
            pstmt.setInt(5, w.visibility());
            pstmt.setString(6, w.ts().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error saving weather: {}", e.getMessage());
        }
    }

    public void saveHistoricalFlight(Flight f) {
        String originIcao = translator.toIcao(f.origin());
        String destIcao = translator.toIcao(f.destination());

        Weather w = fetchClosestWeather(f.origin(), f.ts().toString());
        if (w == null) return;

        String sql = "INSERT OR REPLACE INTO flight_features (flight_id, origin_icao, dest_icao, temp, wind, gust, vis, distance_km, departure_delay, delay_category) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, f.flightId());
            pstmt.setString(2, originIcao);
            pstmt.setString(3, destIcao);
            pstmt.setDouble(4, w.temp());
            pstmt.setDouble(5, w.windSpeed());
            pstmt.setDouble(6, w.windGust());
            pstmt.setDouble(7, w.visibility());
            pstmt.setInt(8, f.distanceKm());
            pstmt.setInt(9, f.departureDelay());
            pstmt.setString(10, categorize(f.departureDelay()));

            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error saving departure feature: {}", e.getMessage());
        }
    }

    public List<Map<String, String>> getHistoricalAirportFlights(String icao) {
        List<Map<String, String>> results = new ArrayList<>();
        // Seleccionamos los datos necesarios de flight_features
        String sql = "SELECT flight_id, dest_icao, delay_category FROM flight_features WHERE origin_icao = ? ORDER BY flight_id DESC LIMIT 5";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, icao);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String destination = rs.getString("dest_icao") != null ? rs.getString("dest_icao") : "N/A";
                results.add(Map.of(
                        // UNIFICAMOS: 'flight' y 'dest' en una sola cadena 'route' para el popup
                        "route", rs.getString("flight_id") + " → " + destination,
                        // RENOMBRAMOS: 'category' a 'prediction' para que app.js lo reconozca
                        "prediction", rs.getString("delay_category")
                ));
            }
        } catch (SQLException e) { logger.error("API Menu error: {}", e.getMessage()); }
        return results;
    }

    public void saveReadyToEatPrediction(Flight f, String predictedCategory) {
        String originIcao = translator.toIcao(f.origin());
        String destIcao = translator.toIcao(f.destination());

        String sql = "INSERT OR REPLACE INTO flight_predictions VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, f.flightId());
            pstmt.setString(2, originIcao);
            pstmt.setString(3, destIcao);
            pstmt.setString(4, f.ts().toString());
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
                        rs.getDouble("temp"), 0, 0,
                        rs.getInt("visibility"),
                        rs.getDouble("wind_speed"),
                        rs.getDouble("wind_gust"),
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

    public List<Map<String, Object>> getAllFlightFeatures() {
        List<Map<String, Object>> results = new ArrayList<>();
        String sql = "SELECT flight_id, origin_icao, dest_icao, temp, wind, gust, vis, distance_km, departure_delay, delay_category FROM flight_features";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             ResultSet rs = conn.createStatement().executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("flightId", rs.getString("flight_id"));
                row.put("originIcao", rs.getString("origin_icao"));
                row.put("destIcao", rs.getString("dest_icao"));
                row.put("temp", rs.getDouble("temp"));
                row.put("wind", rs.getDouble("wind"));
                row.put("gust", rs.getDouble("gust"));
                row.put("vis", rs.getDouble("vis"));
                row.put("distanceKm", rs.getInt("distance_km"));
                row.put("departureDelay", rs.getInt("departure_delay"));
                row.put("delayCategory", rs.getString("delay_category"));
                results.add(row);
            }
        } catch (SQLException e) {
            logger.error("getAllFlightFeatures error: {}", e.getMessage());
        }
        return results;
    }

    public List<Map<String, Object>> getWeatherSeries(String icao, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        String sql = "SELECT airport_icao, temp, wind_speed, wind_gust, visibility, timestamp FROM weather_records WHERE airport_icao = ? ORDER BY timestamp DESC LIMIT ?";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, icao);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("icao", rs.getString("airport_icao"));
                row.put("temp", rs.getDouble("temp"));
                row.put("windSpeed", rs.getDouble("wind_speed"));
                row.put("windGust", rs.getDouble("wind_gust"));
                row.put("visibility", rs.getInt("visibility"));
                row.put("timestamp", rs.getString("timestamp"));
                results.add(row);
            }
        } catch (SQLException e) {
            logger.error("getWeatherSeries error: {}", e.getMessage());
        }
        return results;
    }

    public List<Map<String, Object>> getAllWeatherRecords(int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        String sql = "SELECT airport_icao, temp, wind_speed, wind_gust, visibility, timestamp FROM weather_records ORDER BY timestamp DESC LIMIT ?";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("icao", rs.getString("airport_icao"));
                row.put("temp", rs.getDouble("temp"));
                row.put("windSpeed", rs.getDouble("wind_speed"));
                row.put("windGust", rs.getDouble("wind_gust"));
                row.put("visibility", rs.getInt("visibility"));
                row.put("timestamp", rs.getString("timestamp"));
                results.add(row);
            }
        } catch (SQLException e) {
            logger.error("getAllWeatherRecords error: {}", e.getMessage());
        }
        return results;
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

    public List<Map<String, String>> getRecentAirportPredictions(String icao) {
        List<Map<String, String>> results = new ArrayList<>();
        String sql = "SELECT flight_id, dest_icao, scheduled_time, predicted_category FROM flight_predictions WHERE origin_icao = ? ORDER BY scheduled_time DESC LIMIT 5";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, icao);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                results.add(Map.of(
                        "flight", rs.getString("flight_id"),
                        "dest", rs.getString("dest_icao"),
                        "time", rs.getString("scheduled_time"),
                        "prediction", rs.getString("predicted_category")
                ));
            }
        } catch (SQLException e) { logger.error("API Menu error: {}", e.getMessage()); }
        return results;
    }

    public List<Map<String, Object>> getAirportsWithCurrentDelays() {
        List<Map<String, Object>> result = new ArrayList<>();
        // Obtenemos los aeropuertos del traductor (nombre, coordenadas, etc.)
        Collection<es.ulpgc.dacd.skydelay.business.model.AirportData> airports = translator.getAirports();

        for (es.ulpgc.dacd.skydelay.business.model.AirportData airport : airports) {
            Map<String, Object> airportMap = new HashMap<>();
            airportMap.put("icao", airport.icao());
            airportMap.put("name", airport.name());
            airportMap.put("lat", airport.lat());
            airportMap.put("lng", airport.lon());

            // Calculamos el retraso promedio basado en los últimos vuelos registrados
            double avgDelay = getAverageDelay(airport.icao());
            airportMap.put("delay", avgDelay);

            // Opcional: añadimos los últimos 5 vuelos directamente para el popup del mapa
            airportMap.put("recentFlights", getHistoricalAirportFlights(airport.icao()));

            result.add(airportMap);
        }
        return result;
    }

    private double getAverageDelay(String icao) {
        // Calculamos la media de los últimos 10 vuelos para tener un "estado actual"
        String sql = "SELECT AVG(departure_delay) as avg_delay FROM (" +
                "SELECT departure_delay FROM flight_features " +
                "WHERE origin_icao = ? ORDER BY flight_id DESC LIMIT 10)";

        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, icao);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("avg_delay");
            }
        } catch (SQLException e) {
            logger.error("Error calculating avg delay for {}: {}", icao, e.getMessage());
        }
        return 0.0; // Si no hay datos, asumimos 0 retraso
    }

    private String categorize(int mins) {
        if (mins <= 15) return "none";
        if (mins <= 30) return "low";
        if (mins <= 60) return "moderate";
        return "severe";
    }
}