package es.ulpgc.dacd.skydelay.business.control;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import es.ulpgc.dacd.skydelay.flights.model.Flight;
import es.ulpgc.dacd.skydelay.weather.model.Weather;
import jakarta.jms.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class BusinessEventSubscriber {
    private static final Logger logger = LoggerFactory.getLogger(BusinessEventSubscriber.class);
    private final Connection connection;
    private final DatamartManager datamart;
    private final PredictorService predictor;
    private final Gson gson;

    public BusinessEventSubscriber(Connection connection, DatamartManager datamart, PredictorService predictor) {
        this.connection = connection;
        this.datamart = datamart;
        this.predictor = predictor;
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (json, typeOfT, context) ->
                        Instant.parse(json.getAsString()))
                .create();
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
                    logger.info("Weather recorded for {}: {}°C", weather.icao(), weather.temp());
                }
            } catch (Exception e) {
                logger.error("Error in weather listener: {}", e.getMessage());
            }
        }
    }

    private void processFlight(Message message) {
        if (message instanceof TextMessage textMessage) {
            try {
                Flight flight = gson.fromJson(textMessage.getText(), Flight.class);
                if (flight == null) return;

                if (isHistorical(flight)) {
                    datamart.saveHistoricalFlight(flight);
                    predictor.refreshModel();
                    logger.info("Model updated with departure info from: {}", flight.flightId());
                } else {
                    predictor.processNewFlight(flight);
                    logger.info("Shelf updated: Prediction for flight {}", flight.flightId());
                }
            } catch (Exception e) {
                logger.error("Error processing flight status: {}", e.getMessage());
            }
        }
    }

    private boolean isHistorical(Flight f) {
        String status = f.status().toUpperCase();

        return status.equals("LIVE") ||
                status.equals("LANDED") ||
                status.equals("CANCELLED");
    }
}