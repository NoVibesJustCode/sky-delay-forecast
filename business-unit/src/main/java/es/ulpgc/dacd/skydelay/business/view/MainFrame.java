package es.ulpgc.dacd.skydelay.business.view;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class MainFrame extends Application {

    @Override
    public void start(Stage primaryStage) {
        Label label = new Label("SkyDelay Forecast Dashboard");
        label.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        StackPane root = new StackPane();
        root.getChildren().add(label);

        Scene scene = new Scene(root, 1000, 700);

        primaryStage.setTitle("SkyDelay Forecast System");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

}
