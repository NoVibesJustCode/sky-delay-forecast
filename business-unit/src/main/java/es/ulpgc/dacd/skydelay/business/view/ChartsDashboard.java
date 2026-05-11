package es.ulpgc.dacd.skydelay.business.view;

import es.ulpgc.dacd.skydelay.business.control.DatamartManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ChartsDashboard {
    private final ScrollPane scrollPane;
    private final VBox contentBox;
    private final TilePane tilePane;
    private final HBox kpiBox;
    private DatamartManager datamartManager;

    public ChartsDashboard() {
        this.kpiBox = new HBox(20);
        this.kpiBox.setAlignment(Pos.CENTER);
        this.kpiBox.setPadding(new Insets(20));

        this.tilePane = new TilePane();
        tilePane.setPadding(new Insets(25));
        tilePane.setHgap(30);
        tilePane.setVgap(30);
        tilePane.setAlignment(Pos.CENTER);
        tilePane.setPrefColumns(2);
        tilePane.setStyle("-fx-background-color: transparent;");

        this.contentBox = new VBox(20, kpiBox, tilePane);
        this.contentBox.setStyle("-fx-background-color: #ecf0f1;");
        this.contentBox.setAlignment(Pos.TOP_CENTER);

        this.scrollPane = new ScrollPane(contentBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: #ecf0f1;");
    }

    public void setDatamartManager(DatamartManager datamartManager) {
        this.datamartManager = datamartManager;
    }

    public void refresh() {
        if (datamartManager == null) return;
        Platform.runLater(() -> {
            kpiBox.getChildren().clear();
            tilePane.getChildren().clear();

            List<Map<String, Object>> predictions = datamartManager.getReadyToEatMenu();

            long total = predictions.size();
            long alerts = predictions.stream().filter(p -> "severe".equals(p.get("prediction")) || "moderate".equals(p.get("prediction"))).count();
            String alertPct = total == 0 ? "0%" : String.format("%.1f%%", (alerts * 100.0) / total);

            kpiBox.getChildren().addAll(
                    createKPICard("Total Predictions", String.valueOf(total)),
                    createKPICard("Alert Index", alertPct)
            );

            tilePane.getChildren().addAll(
                    createStatusPieChart(predictions),
                    createDelayBarChart(predictions)
            );
        });
    }

    private Node createKPICard(String title, String value) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(20));
        card.setPrefWidth(200);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 5);");

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size: 14px; -fx-text-fill: #7f8c8d;");

        Label valueLbl = new Label(value);
        valueLbl.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        card.getChildren().addAll(titleLbl, valueLbl);
        return card;
    }

    private Node createStatusPieChart(List<Map<String, Object>> predictions) {
        PieChart chart = new PieChart();
        chart.setTitle("Category Distribution");

        Map<String, Long> counts = predictions.stream()
                .collect(Collectors.groupingBy(p -> (String) p.get("prediction"), Collectors.counting()));

        counts.forEach((cat, count) -> {
            chart.getData().add(new PieChart.Data(cat, count));
        });

        return wrapInContainer(chart);
    }

    private Node createDelayBarChart(List<Map<String, Object>> predictions) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Airports");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Alerts (Severe/Moderate)");

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Most Problematic Airports");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Alerts");

        Map<String, Long> airportAlerts = predictions.stream()
                .filter(p -> "severe".equals(p.get("prediction")) || "moderate".equals(p.get("prediction")))
                .collect(Collectors.groupingBy(
                        p -> ((String) p.get("route")).split("->")[0],
                        Collectors.counting()
                ));

        airportAlerts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .forEach(e -> series.getData().add(new XYChart.Data<>(e.getKey(), e.getValue())));

        chart.getData().add(series);
        return wrapInContainer(chart);
    }

    private VBox wrapInContainer(Chart chart) {
        VBox container = new VBox(chart);
        container.setPadding(new Insets(15));
        container.setPrefSize(500, 400);
        container.setStyle(
                "-fx-background-color: white; " +
                        "-fx-background-radius: 10; " +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 5);"
        );
        return container;
    }

    public Node getView() {
        return scrollPane;
    }
}
