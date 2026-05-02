package es.ulpgc.dacd.skydelay.business.control;

import org.apache.activemq.ActiveMQConnectionFactory;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Controller {
    private static final Logger logger = LoggerFactory.getLogger(Controller.class);
    private final String brokerUrl;
    private final String dbPath;

    public Controller(String brokerUrl, String dbPath) {
        this.brokerUrl = brokerUrl;
        this.dbPath = dbPath;
    }

    public void execute() {
        try {
            DatamartManager datamartManager = new DatamartManager(dbPath);
            datamartManager.initializeDatabase();

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

        } catch (JMSException e) {
            logger.error("JMS Connection error in Business Unit: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in Business Controller: {}", e.getMessage());
        }
    }
}