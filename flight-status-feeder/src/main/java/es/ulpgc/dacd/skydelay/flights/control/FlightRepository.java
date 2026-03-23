package es.ulpgc.dacd.skydelay.flights.control;

import es.ulpgc.dacd.skydelay.flights.model.Flight;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;

public class FlightRepository {
    private final String url = "jdbc:sqlite:storage/db/skydelay.db";

    public FlightRepository() {
        try {
            Files.createDirectories(Paths.get("storage/db"));
            initDatabase();
        } catch (IOException e) {
            System.err.println("Could not create data directory: " + e.getMessage());
        }
    }

    private void initDatabase() {
        String sqlFlights = "CREATE TABLE IF NOT EXISTS flights (" +
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
                "PRIMARY KEY (flight_number, date)" +
                ");";

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sqlFlights);
        } catch (SQLException e) {
            System.err.println("Database initialization error: " + e.getMessage());
        }
    }

    public void save(Flight f) {
        String sql = "INSERT OR REPLACE INTO flights(flight_number, date, origin, destination, " +
                "departure_time, arrival_time, dep_delay, arr_delay, status, aircraft) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?)";

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, f.getFlightId());
            pstmt.setString(2, f.getDate());
            pstmt.setString(3, f.getOrigin());
            pstmt.setString(4, f.getDestination());
            pstmt.setString(5, f.getDepartureTimeUTC());
            pstmt.setString(6, f.getArrivalTimeUTC());
            pstmt.setInt(7, f.getDepartureDelay());
            pstmt.setInt(8, f.getArrivalDelay());
            pstmt.setString(9, f.getStatus());
            pstmt.setString(10, f.getAircraftModel());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error inserting flight data: " + e.getMessage());
        }
    }
}