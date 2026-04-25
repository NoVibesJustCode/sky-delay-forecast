package es.ulpgc.dacd.skydelay.eventstore.control;

import com.google.gson.JsonObject;

public interface EventStore {
    void save(String topic, JsonObject event);
}