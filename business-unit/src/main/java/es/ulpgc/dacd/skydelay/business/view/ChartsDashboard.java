package es.ulpgc.dacd.skydelay.business.view;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.Random;

public class ChartsDashboard {

    private final ScrollPane scrollPane;
    private final GridPane dashboard;

    // PALETA PREMIUM AVIACIÓN
    private static final String BG = "#06121F";
    private static final String CARD = "#0D1B2A";
    private static final String CYAN = "#00D9FF";
    private static final String BLUE = "#2F80ED";
    private static final String AMBER = "#FFB800";
    private static final String RED = "#FF4D6D";
    private static final String GREEN = "#00E676";
    private static final String TEXT = "#D7E3FC";
    private static final String MUTED = "#6E85A6";

    public ChartsDashboard() {

        dashboard = new GridPane();
        dashboard.setPadding(new Insets(35));
        dashboard.setHgap(28);
        dashboard.setVgap(28);

        dashboard.setStyle("""
                -fx-background-color: linear-gradient(to bottom right, #020817, #06121F);
                """);

        scrollPane = new ScrollPane(dashboard);
        scrollPane.setFitToWidth(true);

        scrollPane.setStyle("""
                -fx-background: #020817;
                -fx-background-color: transparent;
                """);

        buildDashboard();
    }

    private void buildDashboard() {

        Node kpi1 = createKpiCard("VUELOS ACTIVOS", "847", "+3.2% vs ayer", CYAN);
        Node kpi2 = createKpiCard("EN HORA", "68.4%", "-1.1% vs ayer", GREEN);
        Node kpi3 = createKpiCard("RETRASO MEDIO", "18 min", "+2 min vs ayer", AMBER);
        Node kpi4 = createKpiCard("CANCELADOS", "23", "+5 vs ayer", RED);

        HBox topCards = new HBox(20, kpi1, kpi2, kpi3, kpi4);
        topCards.setAlignment(Pos.CENTER);

        dashboard.add(topCards, 0, 0, 2, 1);

        dashboard.add(createStatusPieChart(), 0, 1);
        dashboard.add(createTrendAreaChart(), 1, 1);

        dashboard.add(createWindVsDelayScatter(), 0, 2);
        dashboard.add(createComparisonBarChart(), 1, 2);
    }

    // =========================================================
    // KPI CARDS
    // =========================================================

    private Node createKpiCard(String title, String value, String subtitle, String accent) {

        Label titleLabel = new Label(title);
        titleLabel.setStyle("""
                -fx-text-fill: #8AA4C8;
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-letter-spacing: 1.5px;
                """);

        Label valueLabel = new Label(value);
        valueLabel.setStyle("""
                -fx-text-fill: %s;
                -fx-font-size: 42px;
                -fx-font-weight: bold;
                -fx-font-family: 'Consolas';
                """.formatted(accent));

        Label subLabel = new Label(subtitle);
        subLabel.setStyle("""
                -fx-text-fill: #9FB3C8;
                -fx-font-size: 13px;
                """);

        VBox card = new VBox(10, titleLabel, valueLabel, subLabel);

        card.setPadding(new Insets(25));
        card.setPrefWidth(300);

        card.setStyle("""
                -fx-background-color: rgba(13,27,42,0.95);
                -fx-background-radius: 20;
                -fx-border-radius: 20;
                -fx-border-color: %s;
                -fx-border-width: 1.2;
                """.formatted(accent));

        DropShadow glow = new DropShadow();
        glow.setColor(Color.web(accent));
        glow.setRadius(18);

        card.setEffect(glow);

        // Hover premium animation
        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(180), card);
        scaleIn.setToX(1.03);
        scaleIn.setToY(1.03);

        ScaleTransition scaleOut = new ScaleTransition(Duration.millis(180), card);
        scaleOut.setToX(1);
        scaleOut.setToY(1);

        card.setOnMouseEntered(e -> scaleIn.playFromStart());
        card.setOnMouseExited(e -> scaleOut.playFromStart());

        return card;
    }

    // =========================================================
    // PIE CHART
    // =========================================================

    private Node createStatusPieChart() {

        PieChart chart = new PieChart();

        chart.setTitle("ESTADO OPERATIVO GLOBAL");

        PieChart.Data d1 = new PieChart.Data("En Hora", 68.4);
        PieChart.Data d2 = new PieChart.Data("Retraso Leve", 17.2);
        PieChart.Data d3 = new PieChart.Data("Retraso Severo", 9.1);
        PieChart.Data d4 = new PieChart.Data("Cancelados", 5.3);

        chart.getData().addAll(d1, d2, d3, d4);

        chart.setLegendVisible(true);
        chart.setLabelsVisible(false);

        styleChart(chart);

        // COLORES PERSONALIZADOS
        applyPieStyle(d1, GREEN);
        applyPieStyle(d2, AMBER);
        applyPieStyle(d3, RED);
        applyPieStyle(d4, "#9B5DE5");

        // TOOLTIPS + HOVER
        chart.getData().forEach(data -> {

            Tooltip tooltip = new Tooltip(
                    data.getName() + "\n" +
                            String.format("%.1f%%", data.getPieValue())
            );

            tooltip.setStyle("""
                    -fx-background-color: #0B1622;
                    -fx-text-fill: white;
                    -fx-font-size: 13px;
                    -fx-background-radius: 12;
                    """);

            Tooltip.install(data.getNode(), tooltip);

            data.getNode().setOnMouseEntered(e -> {
                data.getNode().setScaleX(1.08);
                data.getNode().setScaleY(1.08);
            });

            data.getNode().setOnMouseExited(e -> {
                data.getNode().setScaleX(1);
                data.getNode().setScaleY(1);
            });
        });

        return wrapCard(chart, "Distribución de puntualidad actual");
    }

    private void applyPieStyle(PieChart.Data data, String color) {

        data.getNode().setStyle("""
                -fx-pie-color: %s;
                """.formatted(color));
    }

    // =========================================================
    // AREA CHART
    // =========================================================

    private Node createTrendAreaChart() {

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();

        xAxis.setLabel("FRANJA HORARIA");
        yAxis.setLabel("TRÁFICO");

        StackedAreaChart<String, Number> chart =
                new StackedAreaChart<>(xAxis, yAxis);

        chart.setTitle("SATURACIÓN OPERATIVA 24H");

        styleChart(chart);

        XYChart.Series<String, Number> flights = new XYChart.Series<>();
        flights.setName("Vuelos");

        flights.getData().add(new XYChart.Data<>("06h", 40));
        flights.getData().add(new XYChart.Data<>("09h", 80));
        flights.getData().add(new XYChart.Data<>("12h", 120));
        flights.getData().add(new XYChart.Data<>("15h", 150));
        flights.getData().add(new XYChart.Data<>("18h", 110));
        flights.getData().add(new XYChart.Data<>("21h", 60));

        XYChart.Series<String, Number> delays = new XYChart.Series<>();
        delays.setName("Retrasos");

        delays.getData().add(new XYChart.Data<>("06h", 5));
        delays.getData().add(new XYChart.Data<>("09h", 15));
        delays.getData().add(new XYChart.Data<>("12h", 28));
        delays.getData().add(new XYChart.Data<>("15h", 42));
        delays.getData().add(new XYChart.Data<>("18h", 25));
        delays.getData().add(new XYChart.Data<>("21h", 10));

        chart.getData().addAll(flights, delays);

        // TOOLTIP INTERACTIVO
        addTooltipsToSeries(flights, " vuelos");
        addTooltipsToSeries(delays, " min retraso");

        return wrapCard(chart, "Predicción dinámica de congestión");
    }

    // =========================================================
    // SCATTER CHART
    // =========================================================

    private Node createWindVsDelayScatter() {

        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();

        xAxis.setLabel("VIENTO km/h");
        yAxis.setLabel("RETRASO min");

        ScatterChart<Number, Number> chart =
                new ScatterChart<>(xAxis, yAxis);

        chart.setTitle("CORRELACIÓN METEOROLÓGICA");

        styleChart(chart);

        XYChart.Series<Number, Number> series =
                new XYChart.Series<>();

        series.setName("Vuelos");

        Random r = new Random();

        for (int i = 0; i < 45; i++) {

            int wind = 10 + r.nextInt(70);
            int delay = wind + r.nextInt(40);

            XYChart.Data<Number, Number> point =
                    new XYChart.Data<>(wind, delay);

            series.getData().add(point);
        }

        chart.getData().add(series);

        // TOOLTIP + EFECTOS
        series.getData().forEach(data -> {

            Tooltip tooltip = new Tooltip(
                    "Viento: " + data.getXValue() + " km/h\n" +
                            "Retraso: " + data.getYValue() + " min"
            );

            Tooltip.install(data.getNode(), tooltip);

            data.getNode().setStyle("""
                    -fx-background-color: #00D9FF, white;
                    -fx-background-radius: 8px;
                    -fx-padding: 5px;
                    """);

            data.getNode().setOnMouseEntered(e -> {
                data.getNode().setScaleX(1.8);
                data.getNode().setScaleY(1.8);
            });

            data.getNode().setOnMouseExited(e -> {
                data.getNode().setScaleX(1);
                data.getNode().setScaleY(1);
            });
        });

        return wrapCard(chart, "Impacto atmosférico en tiempo real");
    }

    // =========================================================
    // BAR CHART
    // =========================================================

    private Node createComparisonBarChart() {

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();

        BarChart<String, Number> chart =
                new BarChart<>(xAxis, yAxis);

        chart.setTitle("RANKING AEROPUERTOS");

        styleChart(chart);

        XYChart.Series<String, Number> series =
                new XYChart.Series<>();

        series.setName("Minutos");

        series.getData().add(new XYChart.Data<>("MAD", 18));
        series.getData().add(new XYChart.Data<>("BCN", 24));
        series.getData().add(new XYChart.Data<>("LPA", 8));
        series.getData().add(new XYChart.Data<>("TFN", 11));
        series.getData().add(new XYChart.Data<>("SVQ", 16));
        series.getData().add(new XYChart.Data<>("BIO", 14));

        chart.getData().add(series);

        chart.setAnimated(true);

        // TOOLTIP + BARRAS NEÓN
        series.getData().forEach(data -> {

            Tooltip.install(
                    data.getNode(),
                    new Tooltip(
                            data.getXValue() + "\n" +
                                    data.getYValue() + " min"
                    )
            );

            data.getNode().setStyle("""
                    -fx-bar-fill: linear-gradient(to top, #00D9FF, #2F80ED);
                    """);

            data.getNode().setOnMouseEntered(e -> {
                data.getNode().setScaleX(1.08);
                data.getNode().setScaleY(1.03);
            });

            data.getNode().setOnMouseExited(e -> {
                data.getNode().setScaleX(1);
                data.getNode().setScaleY(1);
            });
        });

        return wrapCard(chart, "Comparativa nacional de eficiencia");
    }

    // =========================================================
    // CARD WRAPPER
    // =========================================================

    private VBox wrapCard(Chart chart, String subtitle) {

        Label sub = new Label(subtitle);

        sub.setStyle("""
                -fx-text-fill: #7C93B6;
                -fx-font-size: 13px;
                -fx-font-style: italic;
                """);

        VBox box = new VBox(15, chart, sub);

        box.setPadding(new Insets(24));

        box.setStyle("""
                -fx-background-color: rgba(13,27,42,0.96);
                -fx-background-radius: 24;
                -fx-border-radius: 24;
                -fx-border-color: rgba(0,217,255,0.18);
                -fx-border-width: 1.2;
                """);

        box.setEffect(new DropShadow(
                30,
                Color.rgb(0, 217, 255, 0.18)
        ));

        FadeTransition fade = new FadeTransition(
                Duration.millis(900),
                box
        );

        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();

        return box;
    }

    // =========================================================
    // GLOBAL CHART STYLE
    // =========================================================

    private void styleChart(Chart chart) {

        chart.setStyle("""
                -fx-background-color: transparent;
                -fx-text-fill: white;
                """);

        chart.lookupAll(".chart-title").forEach(node ->
                node.setStyle("""
                        -fx-text-fill: #D7E3FC;
                        -fx-font-size: 18px;
                        -fx-font-weight: bold;
                        """));

        chart.setAnimated(true);
    }

    // =========================================================
    // TOOLTIP SERIES
    // =========================================================

    private void addTooltipsToSeries(
            XYChart.Series<String, Number> series,
            String suffix
    ) {

        for (XYChart.Data<String, Number> data : series.getData()) {

            Tooltip tooltip = new Tooltip(
                    data.getXValue() + "\n" +
                            data.getYValue() + suffix
            );

            Tooltip.install(data.getNode(), tooltip);

            data.getNode().setOnMouseEntered(e -> {
                data.getNode().setScaleX(1.4);
                data.getNode().setScaleY(1.4);
            });

            data.getNode().setOnMouseExited(e -> {
                data.getNode().setScaleX(1);
                data.getNode().setScaleY(1);
            });
        }
    }

    // =========================================================
    // VIEW
    // =========================================================

    public Node getView() {
        return scrollPane;
    }
}