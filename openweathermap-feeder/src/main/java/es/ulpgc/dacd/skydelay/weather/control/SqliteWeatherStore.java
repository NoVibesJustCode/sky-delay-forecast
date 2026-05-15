package es.ulpgc.dacd.skydelay.weather.control;

import es.ulpgc.dacd.skydelay.weather.model.Weather;
import es.ulpgc.dacd.skydelay.weather.model.WeatherForecast;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SqliteWeatherStore implements WeatherStore {
    private final String connectionUrl;

    public SqliteWeatherStore(String dbPath) {
        this.connectionUrl = "jdbc:sqlite:" + dbPath;
        initTables();
    }

    private void initTables() {
        String weatherTable = "CREATE TABLE IF NOT EXISTS weather (" +
                "icao TEXT, airport TEXT, description TEXT, temp REAL, feels_like REAL, " +
                "humidity INTEGER, visibility INTEGER, wind_speed REAL, wind_gust REAL, " +
                "clouds INTEGER, timestamp TEXT, captured_at TEXT);";

        String forecastTable = "CREATE TABLE IF NOT EXISTS forecasts (" +
                "icao TEXT, airport TEXT, description TEXT, temp REAL, feels_like REAL, " +
                "humidity INTEGER, visibility INTEGER, wind_speed REAL, wind_gust REAL, " +
                "clouds INTEGER, forecast_ts TEXT, query_ts TEXT);";

        try (Connection conn = DriverManager.getConnection(connectionUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute(weatherTable);
            stmt.execute(forecastTable);
        } catch (SQLException e) {
            System.err.println("Error initializing tables: " + e.getMessage());
        }
    }

    @Override
    public void save(Object obj) {
        if (obj instanceof Weather w) {
            saveWeather(w);
        } else if (obj instanceof WeatherForecast wf) {
            saveForecast(wf);
        }
    }

    private void saveWeather(Weather w) {
        String sql = "INSERT INTO weather(icao, airport, description, temp, feels_like, humidity, " +
                "visibility, wind_speed, wind_gust, clouds, timestamp, captured_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";

        try (Connection conn = DriverManager.getConnection(connectionUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            mapCommonFields(pstmt, w.icao(), w.airport(), w.description(), w.temp(), w.feelsLike(),
                    w.humidity(), w.visibility(), w.windSpeed(), w.windGust(), w.cloudsPct());

            pstmt.setString(11, w.ts().toString());
            pstmt.setString(12, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving weather: " + e.getMessage());
        }
    }

    private void saveForecast(WeatherForecast wf) {
        String sql = "INSERT INTO forecasts(icao, airport, description, temp, feels_like, humidity, " +
                "visibility, wind_speed, wind_gust, clouds, forecast_ts, query_ts) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";

        try (Connection conn = DriverManager.getConnection(connectionUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            mapCommonFields(pstmt, wf.icao(), wf.airport(), wf.description(), wf.temp(), wf.feelsLike(),
                    wf.humidity(), wf.visibility(), wf.windSpeed(), wf.windGust(), wf.cloudsPct());

            pstmt.setString(11, wf.ts().toString());
            pstmt.setString(12, wf.queryTs().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving forecast: " + e.getMessage());
        }
    }

    private void mapCommonFields(PreparedStatement pstmt, String icao, String airport, String desc, double temp,
                                 double feels, int hum, int vis, double speed, double gust, int clouds) throws SQLException {
        pstmt.setString(1, icao);
        pstmt.setString(2, airport);
        pstmt.setString(3, desc);
        pstmt.setDouble(4, temp);
        pstmt.setDouble(5, feels);
        pstmt.setInt(6, hum);
        pstmt.setInt(7, vis);
        pstmt.setDouble(8, speed);
        pstmt.setDouble(9, gust);
        pstmt.setInt(10, clouds);
    }

    @Override
    public void close() {
        System.out.println("SqliteWeatherStore: No persistent connections to close.");
    }
}