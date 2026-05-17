package es.ulpgc.dacd.skydelay.business.control;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import es.ulpgc.dacd.skydelay.business.control.datamart.DatamartManager;
import es.ulpgc.dacd.skydelay.business.control.datamart.FlightHistoricalDAO;
import es.ulpgc.dacd.skydelay.business.control.datamart.FlightPredictionsDAO;
import es.ulpgc.dacd.skydelay.business.control.datamart.WeatherDAO;
import es.ulpgc.dacd.skydelay.business.control.metrics.ModelEvaluationService;
import es.ulpgc.dacd.skydelay.business.control.services.DataStore;
import es.ulpgc.dacd.skydelay.business.control.services.MapDataService;
import es.ulpgc.dacd.skydelay.business.control.services.PredictionService;
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
    private final String eventStorePath;
    private final String dbPath;
    private final String csvPath;
    private final Gson gson;

    private FlightHistoricalDAO historicalDAO;
    private FlightPredictionsDAO predictionsDAO;
    private WeatherDAO weatherDAO;

    private PredictionService predictionService;
    private MapDataService mapDataService;
    private ModelEvaluationService evaluationService;

    public Controller(String brokerUrl, String eventStorePath, String dbPath, String csvPath) {
        this.brokerUrl = brokerUrl;
        this.eventStorePath = eventStorePath;
        this.dbPath = dbPath;
        this.csvPath = csvPath;
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (json, typeOfT, context) ->
                        Instant.parse(json.getAsString()))
                .create();
    }

    public void execute() {
        try {
            DatamartManager datamartManager = new DatamartManager(dbPath);
            datamartManager.initializeDatabase();

            AirportCodeTranslator translator = new AirportCodeTranslator(csvPath);
            this.historicalDAO   = new FlightHistoricalDAO(datamartManager);
            this.predictionsDAO  = new FlightPredictionsDAO(datamartManager);
            this.weatherDAO      = new WeatherDAO(datamartManager);

            DataStore dataStore = new DataStore(historicalDAO, predictionsDAO, weatherDAO);
            this.mapDataService    = new MapDataService(dataStore, translator);
            this.predictionService = new PredictionService(historicalDAO, predictionsDAO, weatherDAO, translator);

            runHistoricalSweep();
            predictionService.refreshModel();

            this.evaluationService = new ModelEvaluationService(historicalDAO, "reports");
            evaluationService.runEvaluation();
            evaluationService.startPeriodicEvaluation(360, 6); // Every 6 hours

            new RestInterface(historicalDAO, predictionsDAO, weatherDAO, mapDataService,
                    translator, evaluationService).start();

            startRealTimeIngestion();

        } catch (Exception e) {
            logger.error("Error starting Controller: {}", e.getMessage(), e);
        }
    }

    private void runHistoricalSweep() {
        logger.info("Starting historical data sweep (Training phase)...");
        EventStoreReader reader = new EventStoreReader(eventStorePath, this.gson);
        reader.processEvents("weather", Weather.class, weatherDAO::save);
        reader.processEvents("flight",  Flight.class,  predictionService::saveHistoricalFlight);
        logger.info("Historical sweep completed. Prediction model ready.");
    }

    private void startRealTimeIngestion() throws JMSException {
        ConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        Connection connection = factory.createConnection();
        connection.setClientID("BusinessUnit-Global");
        connection.start();

        BusinessEventSubscriber subscriber =
                new BusinessEventSubscriber(connection, weatherDAO, predictionService);
        subscriber.subscribeToTopics();

        logger.info("ActiveMQ subscriptions active.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                connection.close();
                logger.info("JMS connection closed successfully.");
            } catch (JMSException e) {
                logger.error("Error closing JMS connection: {}", e.getMessage());
            }
        }));
    }
}