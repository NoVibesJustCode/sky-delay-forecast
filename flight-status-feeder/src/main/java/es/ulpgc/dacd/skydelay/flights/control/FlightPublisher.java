package es.ulpgc.dacd.skydelay.flights.control;

import  es.ulpgc.dacd.skydelay.flights.model.Flight;

public class FlightPublisher {
    private final FlightRepository repository;

    public FlightPublisher(FlightRepository repository) {
        this.repository = repository;
    }

    public void publish(Flight flight) {
        repository.save(flight);
        System.out.println("[DB SAVED] " + flight.flightId() + " at " + java.time.LocalTime.now());
    }
}