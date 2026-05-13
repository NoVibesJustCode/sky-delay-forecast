package es.ulpgc.dacd.skydelay.business.view;

import es.ulpgc.dacd.skydelay.business.control.DatamartManager;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import es.ulpgc.dacd.skydelay.weather.model.Weather;

public class MainFrame extends Application {
    private static MainFrame instance;
    private BorderPane root;
    private MapPresenter mapPresenter;
    private DatamartManager datamartManager;
    private String currentView = "map";
    private VBox infoPanel;

    public static MainFrame getInstance() {
        return instance;
    }

    @Override
    public void start(Stage stage) {
        instance = this;
        this.root = new BorderPane();

        this.mapPresenter = new MapPresenter();

        VBox sidebar = createSidebar();
        root.setLeft(sidebar);

        javafx.scene.layout.HBox topBar = createTopBar();
        root.setTop(topBar);

        showMapView();

        Scene scene = new Scene(root, 1200, 800);
        stage.setTitle("sky-delay-modified Dashboard");
        stage.setOnCloseRequest(e -> { Platform.exit(); System.exit(0); });
        stage.setScene(scene);
        stage.show();

        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(10), e -> refreshCurrentView()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    public void setDatamartManager(DatamartManager datamartManager) {
        this.datamartManager = datamartManager;
        this.mapPresenter.setDatamartManager(datamartManager);
        refreshCurrentView();
    }

    public void showMapView() {
        currentView = "map";
        root.setCenter(mapPresenter.getView());
        if (datamartManager != null) mapPresenter.refresh();
    }


    private void refreshCurrentView() {
        if (datamartManager == null) return;
        if ("map".equals(currentView)) {
            mapPresenter.refresh();
        }
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(20);
        sidebar.setPadding(new Insets(20));
        sidebar.setStyle("-fx-background-color: #2c3e50;");
        sidebar.setPrefWidth(200);
        sidebar.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("SKY-DELAY\nFORECAST");
        title.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 18px; -fx-text-alignment: center;");

        infoPanel = new VBox(10);
        infoPanel.setPadding(new Insets(10, 0, 0, 0));
        infoPanel.setAlignment(Pos.TOP_LEFT);

        sidebar.getChildren().addAll(title, infoPanel);
        return sidebar;
    }

    private javafx.scene.layout.HBox createTopBar() {
        javafx.scene.layout.HBox topBar = new javafx.scene.layout.HBox(20);
        topBar.setPadding(new Insets(10, 20, 10, 20));
        topBar.setStyle("-fx-background-color: #34495e;");
        topBar.setAlignment(Pos.CENTER);

        Button mapBtn = new Button("Live Map");
        mapBtn.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand;");
        mapBtn.setOnAction(e -> showMapView());

        topBar.getChildren().addAll(mapBtn);
        return topBar;
    }

    public void showAirportInfo(String icao, String name) {
        Platform.runLater(() -> {
            infoPanel.getChildren().clear();
            Label header = new Label("Airport:\n" + name + " (" + icao + ")");
            header.setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold; -fx-wrap-text: true;");
            infoPanel.getChildren().add(header);

            if (datamartManager != null) {
                Weather w = datamartManager.fetchClosestWeather(icao, Instant.now().toString());
                if (w != null) {
                    Label weatherLbl = new Label(String.format("Current Weather:\n%.1f°C | Wind: %.1fm/s", w.temp(), w.windSpeed()));
                    weatherLbl.setStyle("-fx-text-fill: #bdc3c7; -fx-font-size: 12px;");
                    infoPanel.getChildren().add(weatherLbl);
                }

                Label histHeader = new Label("\nRecent Flights:");
                histHeader.setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold;");
                infoPanel.getChildren().add(histHeader);

                List<Map<String, String>> histFlights = datamartManager.getHistoricalAirportFlights(icao);
                if (histFlights.isEmpty()) {
                    Label noHist = new Label("No history available.");
                    noHist.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");
                    infoPanel.getChildren().add(noHist);
                } else {
                    for (Map<String, String> f : histFlights) {
                        Label lbl = new Label(f.get("flight") + " to " + f.get("dest") + "\nDelay: " + f.get("delay") + "m (" + f.get("category") + ")");
                        String color = "white";
                        if ("severe".equals(f.get("category"))) color = "#e74c3c";
                        else if ("moderate".equals(f.get("category"))) color = "#e67e22";
                        lbl.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px; -fx-wrap-text: true;");
                        infoPanel.getChildren().add(lbl);
                    }
                }

                Label predHeader = new Label("\nUpcoming Flights:");
                predHeader.setStyle("-fx-text-fill: #2ecc71; -fx-font-weight: bold;");
                infoPanel.getChildren().add(predHeader);

                List<Map<String, String>> flights = datamartManager.getRecentAirportPredictions(icao);
                if (flights.isEmpty()) {
                    Label noData = new Label("No predictions available.");
                    noData.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");
                    infoPanel.getChildren().add(noData);
                } else {
                    for (Map<String, String> f : flights) {
                        String dest = f.get("dest") != null ? f.get("dest") : "N/A";
                        String text = f.get("flight") + " to " + dest + "\nPrediction: " + f.get("prediction");
                        Label lbl = new Label(text);
                        String color = "white";
                        if ("severe".equals(f.get("prediction"))) color = "#e74c3c";
                        else if ("moderate".equals(f.get("prediction"))) color = "#e67e22";
                        lbl.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px; -fx-wrap-text: true;");
                        infoPanel.getChildren().add(lbl);
                    }
                }
            }
        });
    }
}
