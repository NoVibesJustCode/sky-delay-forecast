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

import es.ulpgc.dacd.skydelay.business.view.MainFrame;
import javafx.application.Platform;
import jakarta.jms.*;


public class Controller {
    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final String brokerUrl;
    private final String dbPath;
    private final String csvPath;
    private final String eventStorePath;

    private final Gson gson;
    private final MainFrame gui;
    private PredictorService predictorService;
    private DatamartManager datamartManager;

    public Controller(String brokerUrl, String dbPath, String csvPath, String eventStorePath, MainFrame gui) {
        this.brokerUrl = brokerUrl;
        this.dbPath = dbPath;
        this.csvPath = csvPath;
        this.eventStorePath = eventStorePath;
        this.gui = gui;

        this.gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (json, typeOfT, context) ->
                        Instant.parse(json.getAsString()))
                .create();
    }


    public void execute() {
        try {
            this.datamartManager = new DatamartManager(dbPath, csvPath);
            this.datamartManager.initializeDatabase();

            if (gui != null) {
                Platform.runLater(() -> gui.setDatamartManager(this.datamartManager));
            }

            runHistoricalSweep();

            this.predictorService = new PredictorService(datamartManager);

            RestInterface api = new RestInterface(datamartManager, 7070);
            api.start();

            startRealTimeIngestion();

        } catch (Exception e) {
            logger.error("Error starting Controller: {}", e.getMessage(), e);
        }
    }

    private void runHistoricalSweep() {
        logger.info("Iniciando barrido de datos históricos (Fase de entrenamiento)...");
        EventStoreReader reader = new EventStoreReader(eventStorePath, this.gson);
        reader.processEvents("weather", Weather.class, datamartManager::saveWeather);
        reader.processEvents("flight", Flight.class, datamartManager::saveHistoricalFlight);
        logger.info("Barrido completado. Modelo Predictor listo.");
    }

    private void startRealTimeIngestion() throws JMSException {
        ConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        Connection connection = factory.createConnection();
        connection.setClientID("BusinessUnit-Global");
        connection.start();

        BusinessEventSubscriber subscriber = new BusinessEventSubscriber(connection, datamartManager, predictorService);
        subscriber.subscribeToTopics();

        logger.info("Suscripciones ActiveMQ activadas.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                connection.close();
                logger.info("Conexión JMS cerrada correctamente.");
            } catch (JMSException e) {
                logger.error("Error al cerrar JMS: {}", e.getMessage());
            }
        }));
    }


    public void executeMap() {
        if (gui != null) {
            System.out.println("[Control] Solicitando cambio a vista de MAPA...");
            Platform.runLater(gui::showMapView);
        }
    }

    public void executeDashboard() {
        if (gui != null) {
            System.out.println("[Control] Solicitando cambio a vista de DASHBOARD...");
            Platform.runLater(gui::showDashboardView);
        }
    }
}