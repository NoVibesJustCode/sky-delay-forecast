package es.ulpgc.dacd.skydelay.eventstore.control;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Controller {
    private static final Logger logger = LoggerFactory.getLogger(Controller.class);
    private final String brokerUrl;
    private final String rootPath;

    public Controller(String brokerUrl, String rootPath) {
        this.brokerUrl = brokerUrl;
        this.rootPath = rootPath;
    }

    public void execute() {
        try {
            FileEventStore store = new FileEventStore(rootPath);
            ActiveMQEventSubscriber subscriber = new ActiveMQEventSubscriber(brokerUrl, "EventStoreBuilder", store);

            subscriber.subscribe("weather");
            subscriber.subscribe("flight");

            logger.info("Event Store Builder controller started. Monitoring topics: weather, flight.");
            System.out.println("Event Store Builder is running. Press Ctrl+C to stop.");

        } catch (Exception e) {
            logger.error("Failed to start Event Store Controller: {}", e.getMessage());
        }
    }
}