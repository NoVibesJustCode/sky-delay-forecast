package es.ulpgc.dacd.skydelay.flights.control;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import es.ulpgc.dacd.skydelay.flights.model.Flight;
import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;

import java.time.Instant;

public class ActiveMqFlightStore implements FlightStore {
    private final Connection connection;
    private final MessageProducer producer;
    private final Session session;
    private final Gson gson;

    public ActiveMqFlightStore(String brokerUrl, String topicName) throws JMSException {
        this.gson = new GsonBuilder().registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (src, typeOfSrc, context) ->
                        new JsonPrimitive(src.toString()))
                .create();

        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);

        this.connection = factory.createConnection();
        this.connection.start();

        this.session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Destination destination = session.createTopic(topicName);

        this.producer = session.createProducer(destination);
    }

    @Override
    public void save(Flight flight) {
        String jsonMessage = gson.toJson(flight);
        sendToTopic(jsonMessage);
        }

    private void sendToTopic(String jsonMessage) {
        try {
            TextMessage message = session.createTextMessage(jsonMessage);
            producer.send(message);
            System.out.println("Published to ActiveMQ topic: " + jsonMessage);
        } catch (JMSException e) {
            System.err.println("JMS error: " + e.getMessage());
        }
    }
}
