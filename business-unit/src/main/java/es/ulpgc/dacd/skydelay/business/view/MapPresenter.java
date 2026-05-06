package es.ulpgc.dacd.skydelay.business.view;

import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.concurrent.Worker;

public class MapPresenter {
    private final WebView webView;

    public MapPresenter() {
        this.webView = new WebView();
        WebEngine engine = webView.getEngine();

        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                engine.executeScript("setTimeout(() => map.invalidateSize(), 400)");
            }
        });

        engine.load(getClass().getResource("/map/index.html").toExternalForm());
    }

    public WebView getView() {
        return webView;
    }
}