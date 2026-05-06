package es.ulpgc.dacd.skydelay.business.view;

import com.gluonhq.maps.MapPoint;
import com.gluonhq.maps.MapView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;


public class MapPresenter {
    private final MapView mapView;

    public MapPresenter() {
        this.mapView = new MapView();

        MapPoint madrid = new MapPoint(40.4167, -3.7033);
        mapView.setCenter(madrid);
        mapView.setZoom(6);

        VBox.setVgrow(mapView, Priority.ALWAYS);
    }

    public MapView getView() {
        return mapView;
    }
}