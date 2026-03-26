package es.ulpgc.dacd.skydelay.flights.control;

import es.ulpgc.dacd.skydelay.flights.model.Flight;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FlightRepository {
    private final String databaseUrl;

    public FlightRepository(String dbPath) {
        this.databaseUrl = "jdbc:sqlite:" + dbPath;

        try {
            java.nio.file.Path path = java.nio.file.Paths.get(dbPath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            initDatabase();
        } catch (IOException e) {
            System.err.println("Could not create data directory: " + e.getMessage());
        }
    }

    private void initDatabase() {
        String sqlFlights = "CREATE TABLE IF NOT EXISTS flights (" +
                "captured_at TEXT, " +
                "flight_number TEXT, " +
                "date TEXT, " +
                "origin TEXT, " +
                "destination TEXT, " +
                "departure_time TEXT, " +
                "arrival_time TEXT, " +
                "dep_delay INTEGER, " +
                "arr_delay INTEGER, " +
                "status TEXT, " +
                "aircraft TEXT, " +
                "PRIMARY KEY (captured_at, flight_number, date)" +
                ");";

        try (Connection conn = DriverManager.getConnection(databaseUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sqlFlights);
        } catch (SQLException e) {
            System.err.println("Database initialization error: " + e.getMessage());
        }
    }

    public void save(Flight f) {
        String sql = "INSERT OR REPLACE INTO flights(captured_at, flight_number, date, origin, destination, " +
                "departure_time, arrival_time, dep_delay, arr_delay, status, aircraft) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?)";

        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        try (Connection conn = DriverManager.getConnection(databaseUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, now);
            pstmt.setString(2, f.getFlightId());
            pstmt.setString(3, f.getDate());
            pstmt.setString(4, f.getOrigin());
            pstmt.setString(5, f.getDestination());
            pstmt.setString(6, f.getDepartureTimeUTC());
            pstmt.setString(7, f.getArrivalTimeUTC());
            pstmt.setInt(8, f.getDepartureDelay());
            pstmt.setInt(9, f.getArrivalDelay());
            pstmt.setString(10, f.getStatus());
            pstmt.setString(11, f.getAircraftModel());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error inserting flight data: " + e.getMessage());
        }
    }
}