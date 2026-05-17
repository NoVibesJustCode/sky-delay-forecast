package es.ulpgc.dacd.skydelay.eventstore.control;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.jms.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.BiConsumer;

public class ActiveMQEventSubscriber {
    private static final Logger logger = LoggerFactory.getLogger(ActiveMQEventSubscriber.class);
    private final Connection connection;

    public ActiveMQEventSubscriber(Connection connection) {
        this.connection = connection;
    }

    public void subscribe(String topicName, BiConsumer<String, JsonObject> eventConsumer) {
        try {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(topicName);

            String subscriptionName = "EventStore-Subscription-" + topicName;
            MessageConsumer consumer = session.createDurableSubscriber(topic, subscriptionName);

            consumer.setMessageListener(message -> {
                if (message instanceof TextMessage textMessage) {
                    try {
                        String json = textMessage.getText();
                        JsonObject event = JsonParser.parseString(json).getAsJsonObject();
                        eventConsumer.accept(topicName, event);

                    } catch (JMSException e) {
                        logger.error("Error parsing message from {}: {}", topicName, e.getMessage());
                    }
                }
            });

            logger.info("Successfully subscribed to topic: {} (Durable)", topicName);

        } catch (JMSException e) {
            logger.error("Could not setup durable subscription for {}: {}", topicName, e.getMessage());
        }
    }
}