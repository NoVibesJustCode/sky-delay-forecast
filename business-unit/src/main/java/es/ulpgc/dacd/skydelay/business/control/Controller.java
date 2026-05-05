package es.ulpgc.dacd.skydelay.business.control;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import es.ulpgc.dacd.skydelay.flights.model.Flight;
import es.ulpgc.dacd.skydelay.weather.model.Weather;
import org.apache.activemq.ActiveMQConnectionFactory;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;

public class Controller {
    private static final Logger logger = LoggerFactory.getLogger(Controller.class);
    private final String brokerUrl;
    private final String dbPath;
    private final String csvPath;
    private final String eventStorePath;
    private final Gson gson;

    public Controller(String brokerUrl, String dbPath, String csvPath, String eventStorePath) {
        this.brokerUrl = brokerUrl;
        this.dbPath = dbPath;
        this.csvPath = csvPath;
        this.eventStorePath = eventStorePath;
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (json, typeOfT, context) ->
                        Instant.parse(json.getAsString()))
                .create();
    }

    public void execute() {
        try {
            DatamartManager datamartManager = new DatamartManager(dbPath, csvPath);
            datamartManager.initializeDatabase();

            runHistoricalSweep(datamartManager);

            startRealTimeIngestion(datamartManager);
        } catch (Exception e) {
            logger.error("Unexpected error in Business Controller: {}", e.getMessage());
        }
    }

    private void startRealTimeIngestion(DatamartManager datamartManager) throws JMSException {
        ConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        Connection connection = factory.createConnection();

        connection.setClientID("BusinessUnit-Global");
        connection.start();

        BusinessEventSubscriber subscriber = new BusinessEventSubscriber(connection, datamartManager);
        subscriber.subscribeToTopics();

        logger.info("Business Unit controller started. Database: {}", dbPath);
        System.out.println("Business Unit is running. Press Ctrl+C to stop.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                connection.close();
                logger.info("JMS Connection closed safely.");
            } catch (JMSException e) {
                logger.error("Error closing JMS connection: {}", e.getMessage());
            }
        }));
    }

    private void runHistoricalSweep(DatamartManager datamartManager) {
        logger.info("Starting historical data ingestion...");
        EventStoreReader reader = new EventStoreReader(eventStorePath, this.gson);

        reader.processEvents("weather", Weather.class, datamartManager::saveWeather);
        reader.processEvents("flight", Flight.class, datamartManager::saveFlightAndFeatures);

        logger.info("Historical data loaded successfully.");
    }
}