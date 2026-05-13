package es.ulpgc.dacd.skydelay.business.view;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import es.ulpgc.dacd.skydelay.business.control.DatamartManager;

public class WebMap {
    private final DatamartManager datamartManager;

    public WebMap(DatamartManager datamartManager) {
        this.datamartManager = datamartManager;
    }

    public void start() {
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/map", Location.CLASSPATH);
        }).start(8080);

        app.get("/api/map-data", ctx -> {
            ctx.json(datamartManager.getAirportsWithCurrentDelays());
        });

        System.out.println("🌍 SkyDelay Operational Map: http://localhost:8080/map/index.html");
    }
}