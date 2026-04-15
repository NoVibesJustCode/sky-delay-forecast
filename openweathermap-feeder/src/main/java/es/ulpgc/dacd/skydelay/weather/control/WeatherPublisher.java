package es.ulpgc.dacd.skydelay.weather.control;

import es.ulpgc.dacd.skydelay.weather.model.Weather;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.google.gson.Gson;
import es.ulpgc.dacd.skydelay.weather.model.WeatherEvent;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import javax.jms.Connection;

public class WeatherPublisher implements WeatherStore {

    private Connection connection;
    private Session session;
    private MessageProducer producer;
    private final Gson gson;


    public WeatherPublisher(String brokerUrl, String topicName) throws JMSException {
        this.gson = new Gson();

        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);

        this.connection = factory.createConnection();
        this.connection.start();

        this.session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Destination destination = session.createTopic(topicName);

        this.producer = session.createProducer(destination);
    }

    @Override
    public void save(WeatherEvent event) {
        try {
            String jsonEvent = gson.toJson(event);

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