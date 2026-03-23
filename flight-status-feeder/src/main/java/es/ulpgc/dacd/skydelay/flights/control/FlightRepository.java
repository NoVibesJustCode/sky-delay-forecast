package es.ulpgc.dacd.skydelay.flights.control;

import es.ulpgc.dacd.skydelay.flights.model.Flight;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;

public class FlightRepository {
    private final String url = "jdbc:sqlite:data/skydelay.db";

    public FlightRepository() {
        try {
            Files.createDirectories(Paths.get("data"));
            initDatabase();
        } catch (IOException e) {
            System.err.println("No se pudo crear la carpeta de datos: " + e.getMessage());
        }
    }

    private void initDatabase() {
        String sqlFlights = "CREATE TABLE IF NOT EXISTS flights (" +
                "id TEXT PRIMARY KEY, " +
                "flight_number TEXT, " +
                "origin TEXT, " +
                "destination TEXT, " +
                "date TEXT, " +
                "dep_delay INTEGER, " +
                "arr_delay INTEGER, " +
                "status TEXT, " +
                "aircraft TEXT" +
                ");";

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sqlFlights);
        } catch (SQLException e) {
            System.err.println("Error inicializando DB: " + e.getMessage());
        }
    }

    public void save(Flight f) {
        String sql = "INSERT OR REPLACE INTO flights(id, flight_number, origin, destination, date, dep_delay, arr_delay, status, aircraft) " +
                "VALUES(?,?,?,?,?,?,?,?,?)";

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, f.getFlightId());
            pstmt.setString(2, f.getFlightId());
            pstmt.setString(3, f.getOrigin());
            pstmt.setString(4, f.getDestination());
            pstmt.setString(5, f.getDate());
            pstmt.setInt(6, f.getDepartureDelay());
            pstmt.setInt(7, f.getArrivalDelay());
            pstmt.setString(8, f.getStatus());
            pstmt.setString(9, f.getAircraftModel());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error al insertar vuelo: " + e.getMessage());
        }
    }
}