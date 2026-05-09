package es.ulpgc.dacd.skydelay.business.view;

import com.gluonhq.maps.MapLayer;
import com.gluonhq.maps.MapPoint;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import java.util.ArrayList;
import java.util.List;

public class AirportsLayer extends MapLayer {

    private final List<MarkerData> markers = new ArrayList<>();

    public AirportsLayer() {
        /*
        Añadir los marcadores de los aeropuertos y la info de la predicción
         */


        addMarker(40.4167, -3.7033, "Madrid (MAD) - Retraso: 5 min", Color.GREEN);

    }

    private void addMarker(double lat, double lon, String info, Color color) {
        Circle circle = new Circle(8, color);
        circle.setStroke(Color.WHITE);
        circle.setStrokeWidth(2);

        Tooltip tooltip = new Tooltip(info);
        Tooltip.install(circle, tooltip);

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
