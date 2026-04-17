package es.ulpgc.dacd.skydelay.flights.control;

import es.ulpgc.dacd.skydelay.flights.model.Flight;

public interface FlightStore {
        void save(Flight flight);
        void close();
}
