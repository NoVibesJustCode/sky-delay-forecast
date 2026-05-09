package es.ulpgc.dacd.skydelay.business.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

public class ChartsDashboard {
    private final ScrollPane scrollPane;
    private final TilePane tilePane;

    public ChartsDashboard() {
        this.tilePane = new TilePane();
        tilePane.setPadding(new Insets(25));
        tilePane.setHgap(30);
        tilePane.setVgap(30);
        tilePane.setAlignment(Pos.CENTER);
        tilePane.setPrefColumns(2);
        tilePane.setStyle("-fx-background-color: #ecf0f1;");

        this.scrollPane = new ScrollPane(tilePane);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: #ecf0f1;");

        loadDefaultCharts();
    }

    private void loadDefaultCharts() {
        tilePane.getChildren().addAll(
                createStatusPieChart(),
                createDelayBarChart()
        );
    }

    private Node createStatusPieChart() {
        PieChart chart = new PieChart();
        chart.setTitle("Distribución de Estado de Vuelos");

        chart.getData().add(new PieChart.Data("En hora", 65));
        chart.getData().add(new PieChart.Data("Retrasados", 25));
        chart.getData().add(new PieChart.Data("Cancelados", 10));

        return wrapInContainer(chart);
    }

    private Node createDelayBarChart() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Aeropuertos");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Minutos");

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Retraso Medio por ICAO");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Retraso (min)");
        series.getData().add(new XYChart.Data<>("MAD", 12.5));
        series.getData().add(new XYChart.Data<>("BCN", 18.2));
        series.getData().add(new XYChart.Data<>("LPA", 5.4));
        series.getData().add(new XYChart.Data<>("TFN", 8.9));

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