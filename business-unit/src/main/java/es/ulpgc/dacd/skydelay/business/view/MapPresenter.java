package es.ulpgc.dacd.skydelay.business.view;

import com.gluonhq.maps.MapPoint;
import com.gluonhq.maps.MapView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;


public class MapPresenter {
    private final MapView mapView;

    public MapPresenter() {
        this.mapView = new MapView();

        mapView.setCenter(new MapPoint(48.0, 0.0));
        mapView.setZoom(5);

        mapView.addLayer(new AirportsLayer());

        VBox.setVgrow(mapView, Priority.ALWAYS);
    }

    public MapView getView() {
        return mapView;
    }
}