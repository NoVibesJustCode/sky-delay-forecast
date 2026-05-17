package es.ulpgc.dacd.skydelay.business;

import es.ulpgc.dacd.skydelay.business.control.Controller;
import es.ulpgc.dacd.skydelay.business.view.MainLauncher;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Error: Missing arguments.");
            System.exit(1);
        }

        Controller controller = new Controller(args[0], args[1], args[2], args[3]);
        controller.execute();

        SwingUtilities.invokeLater(() -> {
            MainLauncher gui = new MainLauncher();
            gui.setVisible(true);
        });
    }
}