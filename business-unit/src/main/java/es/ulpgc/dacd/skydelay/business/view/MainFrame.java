package es.ulpgc.dacd.skydelay.business.view;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.application.Platform;

public class MainFrame extends Application {
    private static MainFrame instance;
    private BorderPane root;
    private MapPresenter mapPresenter;
    private ChartsDashboard chartsDashboard;

    public static MainFrame getInstance() {
        return instance;
    }

    @Override
    public void start(Stage stage) {
        instance = this;
        this.root = new BorderPane();

        this.mapPresenter = new MapPresenter();
        this.chartsDashboard = new ChartsDashboard();

        VBox header = createHeader();
        root.setTop(header);

        root.setCenter(mapPresenter.getView());

        Scene scene = new Scene(root, 1200, 800);
        stage.setTitle("SkyDelay Forecast Dashboard");
        stage.setOnCloseRequest(e -> { Platform.exit(); System.exit(0); });
        stage.setScene(scene);
        stage.show();
    }

    public void showMapView() {
        root.setCenter(mapPresenter.getView());
    }

    public void showDashboardView() {
        root.setCenter(chartsDashboard.getView());
    }

    private VBox createHeader() {
        VBox header = new VBox(new Label("SKYDELAY FORECAST"));
        header.setPadding(new Insets(15));
        header.setStyle("-fx-background-color: #2c3e50;");
        header.setAlignment(Pos.CENTER);
        ((Label)header.getChildren().get(0)).setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 18px;");
        return header;
    }
}