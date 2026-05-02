package es.ulpgc.dacd.skydelay.business;

import es.ulpgc.dacd.skydelay.business.control.Controller;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Error: Missing Broker URL. Usage: java -jar ... <broker_url>");
            System.exit(1);
        }

        String brokerUrl = args[0];
        String dbPath = args[1];

        Controller controller = new Controller(brokerUrl, dbPath);
        controller.execute();
    }
}