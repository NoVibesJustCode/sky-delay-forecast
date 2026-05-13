package es.ulpgc.dacd.skydelay.business;

import es.ulpgc.dacd.skydelay.business.control.Controller;
import es.ulpgc.dacd.skydelay.business.view.MainFrame;
import javafx.application.Application;
import javafx.application.Platform;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        System.setProperty("javafx.platform", "desktop");
        System.setProperty("com.gluonhq.charm.down.common.storage.desktop", System.getProperty("user.home"));
        System.setProperty("com.gluonhq.charm.down.common.storage.android", "false");
        System.setProperty("com.gluonhq.charm.down.common.storage.ios", "false");

        Thread guiThread = new Thread(() -> Application.launch(MainFrame.class));
        guiThread.start();

        new Thread(() -> {
            try {
                Thread.sleep(3000);

                while (MainFrame.getInstance() == null) {
                    Thread.sleep(200);
                }

                if (args.length < 4) {
                    System.err.println("Error: Missing arguments.");
                    System.err.println("Uso: java Main <brokerUrl> <dbPath> <csvPath> <eventStorePath>");
                    System.exit(1);
                }

                MainFrame gui = MainFrame.getInstance();

                Controller controller = new Controller(args[0], args[1], args[2], args[3], gui);

                controller.execute();

                Scanner scanner = new Scanner(System.in);
                System.out.println("\n" + "=".repeat(35));
                System.out.println("   SKYDELAY COMMAND LINE");
                System.out.println("=".repeat(35));
                System.out.println("Commands: 'map' | 'stats' | 'exit'");
                System.out.println("\n[SISTEMA] Dashboard Web disponible en: http://localhost:7070");
                while (true) {
                    System.out.print("\n> ");
                    if (scanner.hasNextLine()) {
                        String cmd = scanner.nextLine().trim().toLowerCase();

                        if (cmd.equals("exit")) {
                            Platform.exit();
                            System.exit(0);
                        }

                        switch (cmd) {
                            case "map" -> controller.executeMap();
                            default -> System.out.println("Unknown command.");
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error on thread: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }
}