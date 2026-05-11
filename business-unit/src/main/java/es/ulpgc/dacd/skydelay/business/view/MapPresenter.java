package es.ulpgc.dacd.skydelay.business.view;

import com.gluonhq.maps.MapPoint;
import com.gluonhq.maps.MapView;
import es.ulpgc.dacd.skydelay.business.control.DatamartManager;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class MapPresenter {
    private final MapView mapView;
    private final AirportsLayer airportsLayer;
    private DatamartManager datamartManager;

    public MapPresenter() {
        this.mapView = new MapView();
        mapView.setCenter(new MapPoint(36.0, -5.0));
        mapView.setZoom(5);
        this.airportsLayer = new AirportsLayer();
        mapView.addLayer(airportsLayer);
        VBox.setVgrow(mapView, Priority.ALWAYS);
    }

    public void setDatamartManager(DatamartManager datamartManager) {
        this.datamartManager = datamartManager;
        this.airportsLayer.setDatamartManager(datamartManager);
        refresh();
    }

    public void refresh() {
        if (datamartManager != null) {
            airportsLayer.refresh();
        }
    }

    public MapView getView() {
        return mapView;
    }
}
