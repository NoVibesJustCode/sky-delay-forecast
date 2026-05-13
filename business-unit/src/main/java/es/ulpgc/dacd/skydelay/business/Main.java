package es.ulpgc.dacd.skydelay.business;

import es.ulpgc.dacd.skydelay.business.control.Controller;

public class Main {
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Error: Missing arguments.");
            System.err.println("Uso: java Main <brokerUrl> <dbPath> <csvPath> <eventStorePath>");
            System.exit(1);
        }

        Controller controller = new Controller(args[0], args[1], args[2], args[3]);
        controller.execute();
        System.out.println("\n Dashboard at: http://localhost:7070");
        System.out.println("\n Map at: http://localhost:8080");
    }
}