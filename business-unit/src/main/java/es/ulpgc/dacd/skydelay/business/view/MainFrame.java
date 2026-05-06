package es.ulpgc.dacd.skydelay.business.view;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class MainFrame extends Application {

    @Override
    public void start(Stage stage) {
        MapPresenter mapPresenter = new MapPresenter();
        BorderPane root = new BorderPane();

        VBox header = new VBox(new Label("SKYDELAY FORECAST"));
        header.setPadding(new Insets(15));
        header.setStyle("-fx-background-color: #2c3e50;");
        header.setAlignment(Pos.CENTER);
        ((Label)header.getChildren().get(0)).setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 18px;");

        VBox sidebar = new VBox(new Label("DATOS DE INTERÉS"));
        sidebar.setPadding(new Insets(20));
        sidebar.setPrefWidth(200);
        sidebar.setStyle("-fx-background-color: #f4f4f4; -fx-border-color: #ddd; -fx-border-width: 0 0 0 1;");

        root.setTop(header);
        root.setCenter(mapPresenter.getView());
        root.setRight(sidebar);

        Scene scene = new Scene(root, 1200, 800);
        stage.setTitle("SkyDelay Forecast Dashboard");
        stage.setScene(scene);
        stage.show();
    }
}