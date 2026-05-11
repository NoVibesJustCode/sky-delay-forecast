package es.ulpgc.dacd.skydelay.business.view;

import com.gluonhq.maps.MapLayer;
import com.gluonhq.maps.MapPoint;
import es.ulpgc.dacd.skydelay.business.control.DatamartManager;
import es.ulpgc.dacd.skydelay.business.model.AirportData;
import es.ulpgc.dacd.skydelay.weather.model.Weather;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AirportsLayer extends MapLayer {

    private final List<MarkerData> markers = new ArrayList<>();
    private DatamartManager datamartManager;

    public void setDatamartManager(DatamartManager datamartManager) {
        this.datamartManager = datamartManager;
    }

    public void refresh() {
        if (datamartManager == null) return;
        Platform.runLater(() -> {
            this.getChildren().clear();
            markers.clear();

            List<Map<String, Object>> predictions = datamartManager.getReadyToEatMenu();

            for (AirportData airport : datamartManager.getAirports()) {
                String icao = airport.icao();

                long severe = predictions.stream()
                        .filter(p -> ((String) p.get("route")).startsWith(icao))
                        .filter(p -> "severe".equals(p.get("prediction")))
                        .count();
                long moderate = predictions.stream()
                        .filter(p -> ((String) p.get("route")).startsWith(icao))
                        .filter(p -> "moderate".equals(p.get("prediction")))
                        .count();
                long low = predictions.stream()
                        .filter(p -> ((String) p.get("route")).startsWith(icao))
                        .filter(p -> "low".equals(p.get("prediction")))
                        .count();

                Color color = Color.GREEN;
                if (severe > 0) {
                    color = Color.RED;
                } else if (moderate > 0) {
                    color = Color.ORANGERED;
                } else if (low > 0) {
                    color = Color.ORANGE;
                }

                Weather w = datamartManager.fetchClosestWeather(icao, Instant.now().toString());
                String weatherInfo = w != null ? String.format("Temp: %.1f°C, Wind: %.1fm/s", w.temp(), w.windSpeed()) : "No weather data";

                String info = String.format("%s (%s)\n%s\nAlerts: %d severe, %d moderate, %d low", airport.name(), icao, weatherInfo, severe, moderate, low);

                addMarker(airport.lat(), airport.lon(), info, color, icao, airport.name());
            }
            this.markDirty();
        });
    }

    private void addMarker(double lat, double lon, String info, Color color, String icao, String name) {
        Circle circle = new Circle(8, color);
        circle.setStroke(Color.WHITE);
        circle.setStrokeWidth(2);

        Tooltip tooltip = new Tooltip(info);
        tooltip.setShowDelay(Duration.millis(100));
        Tooltip.install(circle, tooltip);

        circle.setOnMouseClicked(e -> {
            MainFrame.getInstance().showAirportInfo(icao, name);
        });

        markers.add(new MarkerData(new MapPoint(lat, lon), circle));
        this.getChildren().add(circle);
    }

    @Override
    protected void layoutLayer() {
        for (MarkerData data : markers) {
            Point2D point2d = baseMap.getMapPoint(data.point.getLatitude(), data.point.getLongitude());
            data.node.setTranslateX(point2d.getX());
            data.node.setTranslateY(point2d.getY());
        }
    }

    private record MarkerData(MapPoint point, Node node) {}
}
