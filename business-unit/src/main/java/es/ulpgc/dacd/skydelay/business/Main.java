package es.ulpgc.dacd.skydelay.business;

import es.ulpgc.dacd.skydelay.business.control.Controller;

public class Main {
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Error: Missing arguments. Usage: <brokerUrl> <eventStorePath> <dbPath> <csvPath>");
            System.exit(1);
        }

        Controller controller = new Controller(args[0], args[1], args[2], args[3]);
        controller.execute();
    }
}
