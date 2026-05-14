package es.ulpgc.dacd.skydelay.eventstore;

import es.ulpgc.dacd.skydelay.eventstore.control.Controller;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Error: Missing Broker URL. Usage: java -jar ... <broker_url>");
            System.exit(1);
        }

        String brokerUrl = args[0];
        Controller controller = new Controller(brokerUrl, args[1]);
        controller.execute();
    }
}