package es.ulpgc.dacd.skydelay.weather.control;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import es.ulpgc.dacd.skydelay.weather.model.Weather;
import java.time.Instant;
import com.google.gson.Gson;
import es.ulpgc.dacd.skydelay.weather.model.WeatherForecast;
import org.apache.activemq.ActiveMQConnectionFactory;
import jakarta.jms.*;
import jakarta.jms.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActiveMQWeatherStore implements WeatherStore {
    private final Connection connection;
    private final Session session;
    private final MessageProducer producer;
    private final Destination currentTopic;
    private final Destination forecastTopic;
    private final Gson gson;
    private static final Logger logger = LoggerFactory.getLogger(ActiveMQWeatherStore.class);


    public ActiveMQWeatherStore(String brokerUrl, String currentTopicName, String forecastTopicName) throws JMSException {

        this.gson = new GsonBuilder().registerTypeAdapter(Instant.class,
                (JsonSerializer<Instant>) (src, typeOfSrc, context) -> new JsonPrimitive(src.toString())).create();

        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        this.connection = factory.createConnection();
        this.connection.start();

        this.session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        this.currentTopic = session.createTopic(currentTopicName);
        this.forecastTopic = session.createTopic(forecastTopicName);

        this.producer = session.createProducer(null);
    }

    @Override
    public void save(Object event) {
        try {
            String jsonEvent = gson.toJson(event);
            TextMessage message = session.createTextMessage(jsonEvent);

            if (event instanceof Weather) {
                producer.send(currentTopic, message);
                System.out.println("Published to CURRENT: " + ((Weather) event).icao());
            } else if (event instanceof WeatherForecast) {
                producer.send(forecastTopic, message);
                System.out.println("Published to FORECAST: " + ((WeatherForecast) event).icao());
            }

        } catch (JMSException e) {
            System.err.println("JMS Error: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        try {
            if (producer != null) producer.close();
            if (session != null) session.close();
            if (connection != null) connection.close();
            System.out.println("ActiveMQ connection closed safely.");
        } catch (JMSException e) {
            System.err.println("Error closing JMS connection: " + e.getMessage());
        }

    }
}