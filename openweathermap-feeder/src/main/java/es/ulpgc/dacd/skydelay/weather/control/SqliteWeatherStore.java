package es.ulpgc.dacd.skydelay.weather.control;

import es.ulpgc.dacd.skydelay.weather.model.Weather;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SqliteWeatherStore implements WeatherStore {
    private final String connectionUrl;

    public SqliteWeatherStore(String dbPath) {
        this.connectionUrl = "jdbc:sqlite:" + dbPath;
        initTable();
    }

    private void initTable() {
        String sql = "CREATE TABLE IF NOT EXISTS weather (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "icao TEXT," +
                "airport TEXT," +
                "description TEXT," +
                "temp REAL," +
                "feels_like REAL," +
                "humidity INTEGER," +
                "visibility INTEGER," +
                "wind_speed REAL," +
                "wind_gust REAL," +
                "clouds INTEGER," +
                "timestamp INTEGER," +
                "captured_at TEXT" +
                ");";

        try (Connection conn = DriverManager.getConnection(connectionUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.err.println("Error al inicializar la tabla: " + e.getMessage());
        }
    }

    @Override
    public void save(Weather w) {
        String sql = "INSERT INTO weather(icao, airport, description, temp, feels_like, humidity, " +
                "visibility, wind_speed, wind_gust, clouds, timestamp, captured_at) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";

        try (Connection conn = DriverManager.getConnection(connectionUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, w.icao());
            pstmt.setString(2, w.airport());
            pstmt.setString(3, w.description());
            pstmt.setDouble(4, w.temp());
            pstmt.setDouble(5, w.feelsLike());
            pstmt.setInt(6, w.humidity());
            pstmt.setInt(7, w.visibility());
            pstmt.setDouble(8, w.windSpeed());
            pstmt.setDouble(9, w.windGust());
            pstmt.setInt(10, w.cloudsPct());
            pstmt.setString(11, w.ts().toString());
            pstmt.setString(12, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al insertar datos: " + e.getMessage());
        }
    }
}