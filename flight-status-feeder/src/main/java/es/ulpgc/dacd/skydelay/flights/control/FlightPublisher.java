package es.ulpgc.dacd.skydelay.flights.control;

import  es.ulpgc.dacd.skydelay.flights.model.Flight;

public class FlightPublisher {
    private final FlightRepository repository = new FlightRepository();

    public void publish(Flight flight) {
        repository.save(flight);
        System.out.println("[DB SAVED] " + flight.getFlightId());
    }
}