package es.ulpgc.dacd.skydelay.flights.model;

public class Flight {
    private final String flightId;
    private final String origin;
    private final String destination;
    private final String date;
    private final String departureTimeUTC;
    private final String arrivalTimeUTC;
    private final String status;
    private final int departureDelay;
    private final int arrivalDelay;
    private final int distanceKm;
    private final String aircraftModel;

    public Flight(String flightId, String origin, String destination, String date,
                  String departureTimeUTC, String arrivalTimeUTC, String status,
                  int departureDelay, int arrivalDelay, int distanceKm, String aircraftModel) {
        this.flightId = flightId;
        this.origin = origin;
        this.destination = destination;
        this.date = date;
        this.departureTimeUTC = departureTimeUTC;
        this.arrivalTimeUTC = arrivalTimeUTC;
        this.status = status;
        this.departureDelay = departureDelay;
        this.arrivalDelay = arrivalDelay;
        this.distanceKm = distanceKm;
        this.aircraftModel = aircraftModel;
    }

    public String getFlightId() {
        return flightId;
    }

    public String getOrigin() {
        return origin;
    }

    public String getDestination() {
        return destination;
    }

    public String getDate() {
        return date;
    }

    public String getDepartureTimeUTC() {
        return departureTimeUTC;
    }

    public String getArrivalTimeUTC() {
        return arrivalTimeUTC;
    }

    public String getStatus() {
        return status;
    }

    public int getArrivalDelay() {
        return arrivalDelay;
    }

    public int getDepartureDelay() {
        return departureDelay;
    }

    public int getDistanceKm() {
        return distanceKm;
    }

    public String getAircraftModel() {
        return aircraftModel;
    }

    @Override
    public String toString() {
        return String.format("Flight %s: %s -> %s | Total Delay: %dm | Aircraft: %s",
                flightId, origin, destination, departureDelay+arrivalDelay, aircraftModel);
    }
}