package es.ulpgc.dacd.skydelay.business;

import es.ulpgc.dacd.skydelay.business.control.Controller;
import es.ulpgc.dacd.skydelay.business.view.MainFrame;
import javafx.application.Application;

public class Main {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Error: Missing Broker URL. Usage: java -jar ... <broker_url>");
            System.exit(1);
        }

        new Thread(() -> {
            Controller controller = new Controller(args[0], args[1], args[2]);
            controller.execute();
        }).start();

        Application.launch(MainFrame.class, args);
    }
}