package es.ulpgc.dacd.skydelay.eventstore.control;

import org.apache.activemq.ActiveMQConnectionFactory;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
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
            EventStore store = new FileEventStore(rootPath);

            ConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
            Connection connection = factory.createConnection();
            connection.setClientID("EventStoreBuilder-Global");
            connection.start();

            ActiveMQEventSubscriber subscriber = new ActiveMQEventSubscriber(connection);

            subscriber.subscribe("weather", store::save);
            subscriber.subscribe("flight", store::save);

            logger.info("Event Store Builder controller started. Monitoring topics: weather, flight.");
            System.out.println("Event Store Builder is running (Shared Connection). Press Ctrl+C to stop.");

        } catch (JMSException e) {
            logger.error("JMS Connection error: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error starting Event Store Controller: {}", e.getMessage());
        }
    }
}