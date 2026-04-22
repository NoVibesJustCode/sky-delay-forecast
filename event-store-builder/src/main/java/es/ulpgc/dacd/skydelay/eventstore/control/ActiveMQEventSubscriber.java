package es.ulpgc.dacd.skydelay.eventstore.control;

import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActiveMQEventSubscriber {
    private static final Logger logger = LoggerFactory.getLogger(ActiveMQEventSubscriber.class);
    private final String brokerUrl;
    private final String clientID;
    private final FileEventStore store;

    public ActiveMQEventSubscriber(String brokerUrl, String clientID, FileEventStore store) {
        this.brokerUrl = brokerUrl;
        this.clientID = clientID;
        this.store = store;
    }

    public void subscribe(String topicName) {
        try {
            ConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
            Connection connection = factory.createConnection();
            
            connection.setClientID(clientID + "-" + topicName);
            connection.start();

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(topicName);
            
            MessageConsumer consumer = session.createDurableSubscriber(topic, "EventStore-Subscription");

            consumer.setMessageListener(message -> {
                if (message instanceof TextMessage) {
                    try {
                        String text = ((TextMessage) message).getText();
                        store.save(topicName, text);
                    } catch (JMSException e) {
                        logger.error("Error reading message: {}", e.getMessage());
                    }
                }
            });

            logger.info("Successfully subscribed to topic: {} (Durable)", topicName);

        } catch (JMSException e) {
            logger.error("Could not setup durable subscription for {}: {}", topicName, e.getMessage());
        }
    }
}
