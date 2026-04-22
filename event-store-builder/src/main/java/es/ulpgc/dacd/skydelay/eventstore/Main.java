package es.ulpgc.dacd.skydelay.eventstore;

import es.ulpgc.dacd.skydelay.eventstore.control.ActiveMQEventSubscriber;
import es.ulpgc.dacd.skydelay.eventstore.control.FileEventStore;

public class Main {
    public static void main(String[] args) {
        String brokerUrl = args[0];
        FileEventStore store = new FileEventStore("eventstore");

        ActiveMQEventSubscriber subscriber = new ActiveMQEventSubscriber(brokerUrl, "EventStoreBuilder", store);

        subscriber.subscribe("weather");
        subscriber.subscribe("flight");

        System.out.println("Event Store Builder is running. Press Ctrl+C to stop.");
    }
}