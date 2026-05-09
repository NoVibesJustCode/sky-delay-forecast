package es.ulpgc.dacd.skydelay.business;

import es.ulpgc.dacd.skydelay.business.control.Controller;
import es.ulpgc.dacd.skydelay.business.view.MainFrame;
import javafx.application.Application;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        new Thread(() -> Application.launch(MainFrame.class)).start();

        while (MainFrame.getInstance() == null) Thread.sleep(100);

        MainFrame gui = MainFrame.getInstance();
        Controller controller = new Controller(args[0], gui);

        controller.startSubscribers(args[1], args[2]);

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("Command (map/stats/exit): ");
            String cmd = scanner.nextLine().toLowerCase();
            if (cmd.equals("map")) controller.executeMap();
            else if (cmd.equals("stats")) controller.executeDashboard();
            else if (cmd.equals("exit")) System.exit(0);
        }
    }
}