package es.ulpgc.dacd.skydelay.business;

import es.ulpgc.dacd.skydelay.business.control.Controller;

public class Main {
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Error: Missing arguments!");
            System.exit(1);
        }

        String brokerUrl = args[0];
        String dbPath = args[1];
        String csvPath = args[2];
        String eventsPath = args[3];

        Controller controller = new Controller(brokerUrl, dbPath, csvPath, eventsPath);
        controller.execute();
    }
}