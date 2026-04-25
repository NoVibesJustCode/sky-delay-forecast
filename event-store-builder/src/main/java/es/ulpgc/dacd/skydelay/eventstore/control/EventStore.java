package es.ulpgc.dacd.skydelay.eventstore.control;

public interface EventStore {
    void save(String topic, String event);
}