package es.ulpgc.dacd.skydelay.business.control;

import es.ulpgc.dacd.skydelay.business.control.DatamartManager;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

public class RestInterface {
    private final DatamartManager datamartManager;

    public RestInterface(DatamartManager datamartManager) {
        this.datamartManager = datamartManager;
    }

    public void start() {
        Javalin dashboardApp = Javalin.create(config -> {
            config.staticFiles.add("/public/dashboard", Location.CLASSPATH);
        }).start(7070);

        Javalin mapApp = Javalin.create(config -> {
            config.staticFiles.add("/public/map", Location.CLASSPATH);
        }).start(8080);


        dashboardApp.get("/api/data", ctx -> ctx.json(datamartManager.getReadyToEatMenu()));
        setupCommonRoutes(dashboardApp);

        mapApp.get("/api/data", ctx -> ctx.json(datamartManager.getAirportsWithCurrentDelays()));
        setupCommonRoutes(mapApp);

    }

    private void setupCommonRoutes(Javalin app) {
        app.get("/api/airports", ctx -> ctx.json(datamartManager.getAirports()));
        app.get("/api/weather-records", ctx -> {
            int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(500);
            ctx.json(datamartManager.getAllWeatherRecords(limit));
        });
    }


}