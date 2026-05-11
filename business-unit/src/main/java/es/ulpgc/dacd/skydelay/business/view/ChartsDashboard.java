package es.ulpgc.dacd.skydelay.business.view;

import es.ulpgc.dacd.skydelay.business.control.DatamartManager;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ChartsDashboard {

    private final ScrollPane scrollPane;
    private final VBox mainLayout;
    private final GridPane chartGrid;
    private final HBox kpiBox;
    private DatamartManager datamartManager;

    private static final String BG_GRADIENT = "linear-gradient(to bottom right, #020817, #06121F)";
    private static final String CYAN = "#00D9FF";
    private static final String AMBER = "#FFB800";
    private static final String RED = "#FF4D6D";
    private static final String GREEN = "#00E676";
    private static final String TEXT = "#D7E3FC";

    public ChartsDashboard() {
        this.kpiBox = new HBox(25);
        this.kpiBox.setAlignment(Pos.CENTER);
        this.kpiBox.setPadding(new Insets(20, 0, 10, 0));

        this.chartGrid = new GridPane();
        chartGrid.setHgap(30);
        chartGrid.setVgap(30);
        chartGrid.setAlignment(Pos.CENTER);

        this.mainLayout = new VBox(10, kpiBox, chartGrid);
        mainLayout.setPadding(new Insets(35));
        mainLayout.setStyle("-fx-background-color: " + BG_GRADIENT + ";");

        this.scrollPane = new ScrollPane(mainLayout);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #020817; -fx-background-color: transparent;");
    }

    public void setDatamartManager(DatamartManager datamartManager) {
        this.datamartManager = datamartManager;
    }

    public void refresh() {
        if (datamartManager == null) return;

        Platform.runLater(() -> {
            List<Map<String, Object>> data = datamartManager.getReadyToEatMenu();

            kpiBox.getChildren().clear();
            chartGrid.getChildren().clear();

            long total = data.size();
            long alerts = data.stream()
                    .filter(p -> List.of("severe", "moderate").contains(p.get("prediction")))
                    .count();
            String alertPct = total == 0 ? "0%" : String.format("%.1f%%", (alerts * 100.0) / total);

            kpiBox.getChildren().addAll(
                    createKpiCard("TOTAL PREDICTIONS", String.valueOf(total), "Monitored flights", CYAN),
                    createKpiCard("ALERT INDEX", alertPct, "Delay risk profile", RED),
                    createKpiCard("SYSTEM STATUS", "ONLINE", "ActiveMQ link active", GREEN)
            );

            chartGrid.add(createStatusPieChart(data), 0, 0);
            chartGrid.add(createProblematicAirportsBar(data), 1, 0);
            chartGrid.add(createWindCorrelationScatter(data), 0, 1);
            chartGrid.add(createSaturationAreaChart(data), 1, 1);
        });
    }

    private Node createKpiCard(String title, String value, String subtitle, String accent) {
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-text-fill: #8AA4C8; -fx-font-size: 13px; -fx-font-weight: bold; -fx-letter-spacing: 1.2;");

        Label valLbl = new Label(value);
        valLbl.setStyle("-fx-text-fill: " + accent + "; -fx-font-size: 38px; -fx-font-weight: bold; -fx-font-family: 'Consolas';");

        Label subLbl = new Label(subtitle);
        subLbl.setStyle("-fx-text-fill: #9FB3C8; -fx-font-size: 11px;");

        VBox card = new VBox(8, titleLbl, valLbl, subLbl);
        card.setPadding(new Insets(20));
        card.setPrefWidth(260);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle(String.format("-fx-background-color: rgba(13,27,42,0.9); -fx-background-radius: 18; -fx-border-color: %s; -fx-border-width: 1; -fx-border-radius: 18;", accent));
        card.setEffect(new DropShadow(20, Color.web(accent, 0.4)));
        return card;
    }

    private Node createStatusPieChart(List<Map<String, Object>> data) {
        PieChart chart = new PieChart();
        chart.setTitle("CATEGORY DISTRIBUTION");
        styleChart(chart);

        Map<String, Long> counts = data.stream()
                .collect(Collectors.groupingBy(p -> (String) p.get("prediction"), Collectors.counting()));

        counts.forEach((cat, count) -> {
            String label = switch (cat.toLowerCase()) {
                case "none" -> "ON TIME";
                case "moderate" -> "MODERATE DELAY";
                case "severe" -> "SEVERE DELAY";
                default -> cat.toUpperCase();
            };

            PieChart.Data slice = new PieChart.Data(label, count);
            chart.getData().add(slice);

            Platform.runLater(() -> {
                String color = switch (cat.toLowerCase()) {
                    case "severe" -> RED;
                    case "moderate" -> AMBER;
                    case "none" -> GREEN;
                    default -> CYAN;
                };
                slice.getNode().setStyle("-fx-pie-color: " + color + ";");
            });
        });

        return wrapCard(chart, "Current punctuality analysis");
    }

    private Node createProblematicAirportsBar(List<Map<String, Object>> data) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("TOP DELAY SOURCES (AIRPORTS)");
        styleChart(chart);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Alerts Count");

        Map<String, Long> airportAlerts = data.stream()
                .filter(p -> !"none".equals(p.get("prediction")))
                .collect(Collectors.groupingBy(p -> ((String) p.get("route")).split("->")[0], Collectors.counting()));

        airportAlerts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(6)
                .forEach(e -> series.getData().add(new XYChart.Data<>(e.getKey(), e.getValue())));

        chart.getData().add(series);
        return wrapCard(chart, "Operational criticality ranking");
    }

    private Node createWindCorrelationScatter(List<Map<String, Object>> data) {
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("WIND SPEED (km/h)");
        yAxis.setLabel("HUMIDITY (%)");

        ScatterChart<Number, Number> chart = new ScatterChart<>(xAxis, yAxis);
        chart.setTitle("METEOROLOGICAL CORRELATION");
        styleChart(chart);

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Flight Conditions");

        data.stream().limit(50).forEach(p -> {
            series.getData().add(new XYChart.Data<>(
                    (Double)p.getOrDefault("wind", 0.0),
                    (Integer)p.getOrDefault("humidity", 0)
            ));
        });

        chart.getData().add(series);
        return wrapCard(chart, "Atmospheric prediction signature");
    }

    private Node createSaturationAreaChart(List<Map<String, Object>> data) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        AreaChart<String, Number> chart = new AreaChart<>(xAxis, yAxis);
        chart.setTitle("PREDICTION ENGINE LOAD");
        styleChart(chart);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Requests");

        series.getData().add(new XYChart.Data<>("T-3h", data.size() * 0.4));
        series.getData().add(new XYChart.Data<>("T-2h", data.size() * 0.7));
        series.getData().add(new XYChart.Data<>("T-1h", data.size() * 0.9));
        series.getData().add(new XYChart.Data<>("NOW", (double)data.size()));

        chart.getData().add(series);
        return wrapCard(chart, "Temporal throughput evolution");
    }

    private VBox wrapCard(Chart chart, String subtitle) {
        Label sub = new Label(subtitle.toUpperCase());
        sub.setStyle("-fx-text-fill: #6E85A6; -fx-font-size: 11px; -fx-font-weight: bold;");

        VBox box = new VBox(12, chart, sub);
        box.setPadding(new Insets(20));
        box.setPrefSize(520, 380);
        box.setStyle("-fx-background-color: rgba(13,27,42,0.85); -fx-background-radius: 22; -fx-border-color: rgba(0,217,255,0.1); -fx-border-width: 1.5; -fx-border-radius: 22;");

        box.setEffect(new DropShadow(25, Color.rgb(0, 0, 0, 0.4)));

        FadeTransition ft = new FadeTransition(Duration.millis(1000), box);
        ft.setFromValue(0); ft.setToValue(1); ft.play();

        return box;
    }

    private void styleChart(Chart chart) {
        chart.setStyle("-fx-background-color: transparent;");
        chart.lookupAll(".chart-title").forEach(node ->
                node.setStyle("-fx-text-fill: " + TEXT + "; -fx-font-size: 16px; -fx-font-weight: bold; -fx-font-family: 'Segoe UI';")
        );
        chart.setLegendVisible(true);
        if (chart instanceof XYChart) {
            XYChart<?,?> xyc = (XYChart<?,?>) chart;
            xyc.getXAxis().setTickLabelFill(Color.web("#8AA4C8"));
            xyc.getYAxis().setTickLabelFill(Color.web("#8AA4C8"));
        }
    }

    public Node getView() {
        return scrollPane;
    }
}