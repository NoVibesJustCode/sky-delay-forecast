package es.ulpgc.dacd.skydelay.business.control;

import es.ulpgc.dacd.skydelay.business.view.MainFrame;
import javafx.application.Platform;
import org.apache.activemq.ActiveMQConnectionFactory;
import jakarta.jms.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Controller {
    private static final Logger logger = LoggerFactory.getLogger(Controller.class);
    private final String brokerUrl;
    private final MainFrame gui;

    public Controller(String brokerUrl, MainFrame gui) {
        this.brokerUrl = brokerUrl;
        this.gui = gui;
    }

    public void startSubscribers(String dbPath, String csvPath) {
        try {
            DatamartManager datamartManager = new DatamartManager(dbPath, csvPath);
            datamartManager.initializeDatabase();

            ConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
            Connection connection = factory.createConnection();
            connection.start();

            BusinessEventSubscriber subscriber = new BusinessEventSubscriber(connection, datamartManager);
            subscriber.subscribeToTopics();

            logger.info("Subscribers active for DB: {}", dbPath);
        } catch (JMSException e) {
            logger.error("JMS Error: {}", e.getMessage());
        }
    }

    public void executeMap() {
        System.out.println("[Control] Cambiando a vista de MAPA...");
        Platform.runLater(gui::showMapView);
    }

    public void executeDashboard() {
        System.out.println("[Control] Cambiando a vista de DASHBOARD...");
        Platform.runLater(gui::showDashboardView);
    }
}