package es.ulpgc.dacd.skydelay.weather.control;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import es.ulpgc.dacd.skydelay.weather.model.Weather;

import java.time.Instant;

import com.google.gson.Gson;
import org.apache.activemq.ActiveMQConnectionFactory;

import jakarta.jms.*;
import jakarta.jms.Connection;

public class WeatherPublisher implements WeatherStore {

    private Connection connection;
    private final Session session;
    private final MessageProducer producer;
    private final Gson gson;


    public WeatherPublisher(String brokerUrl, String topicName) throws JMSException {

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
    public void save(Weather weather) {
        try {
            String jsonEvent = gson.toJson(weather);

            System.out.println(jsonEvent);

            TextMessage message = session.createTextMessage(jsonEvent);
            producer.send(message);

            System.out.println("Evento publicado en ActiveMQ: " + jsonEvent);

        } catch (JMSException e) {
            System.err.println("Error al publicar el evento: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            if (producer != null) producer.close();
            if (session != null) session.close();
            if (connection != null) connection.close();
        } catch (JMSException e) {
            System.err.println("Error cerrando conexión JMS: " + e.getMessage());
        }

    }
}