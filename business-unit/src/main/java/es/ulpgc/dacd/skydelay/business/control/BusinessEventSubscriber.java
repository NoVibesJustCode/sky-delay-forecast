package es.ulpgc.dacd.skydelay.business.control;

import com.google.gson.Gson;
import es.ulpgc.dacd.skydelay.flights.model.Flight;
import es.ulpgc.dacd.skydelay.weather.model.Weather;
import jakarta.jms.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BusinessEventSubscriber {
    private static final Logger logger = LoggerFactory.getLogger(BusinessEventSubscriber.class);
    private final Connection connection;
    private final DatamartManager datamart;
    private final Gson gson;

    public BusinessEventSubscriber(Connection connection, DatamartManager datamart) {
        this.connection = connection;
        this.datamart = datamart;
        this.gson = new Gson();
    }

    public void subscribeToTopics() {
        subscribe("weather", this::processWeather);
        subscribe("flight", this::processFlight);
    }

    private void subscribe(String topicName, MessageListener listener) {
        try {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(topicName);

            String subscriptionName = "BusinessUnit-" + topicName;
            MessageConsumer consumer = session.createDurableSubscriber(topic, subscriptionName);

            consumer.setMessageListener(listener);
            logger.info("Business Unit subscribed to: {}", topicName);
        } catch (JMSException e) {
            logger.error("Error subscribing to {}: {}", topicName, e.getMessage());
        }
    }

    private void processWeather(Message message) {
        if (message instanceof TextMessage textMessage) {
            try {
                String json = textMessage.getText();
                Weather weather = gson.fromJson(json, Weather.class);

                if (weather != null) {
                    datamart.saveWeather(weather);
                    logger.info("Weather ingested: {}", weather.icao());
                }
            } catch (JMSException e) {
                logger.error("JMS Error in weather listener: {}", e.getMessage());
            } catch (Exception e) {
                logger.error("Parsing Error in weather listener: {}", e.getMessage());
            }
        }
    }

    private void processFlight(Message message) {
        if (message instanceof TextMessage textMessage) {
            try {
                String json = textMessage.getText();
                Flight flight = gson.fromJson(json, Flight.class);

                if (flight != null) {
                    datamart.saveFlightAndFeatures(flight);
                    logger.info("Flight and features processed: {}", flight.flightId());
                }
            } catch (JMSException e) {
                logger.error("JMS Error in flight listener: {}", e.getMessage());
            } catch (Exception e) {
                logger.error("Parsing Error in flight listener: {}", e.getMessage());
            }
        }
    }
}