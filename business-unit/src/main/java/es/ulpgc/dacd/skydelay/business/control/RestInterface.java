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

        setupRoutes(dashboardApp);
        setupRoutes(mapApp);

        System.out.println("Dashboard disponible en: http://localhost:7070");
        System.out.println("Mapa disponible en: http://localhost:8080");
    }

    private void setupRoutes(Javalin app) {
        app.get("/api/data", ctx -> ctx.json(datamartManager.getReadyToEatMenu()));

        app.get("/api/flight-features", ctx -> ctx.json(datamartManager.getAllFlightFeatures()));

        app.get("/api/weather-series", ctx -> {
            String icao = ctx.queryParamAsClass("icao", String.class).getOrDefault("LEMD");
            int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(200);
            ctx.json(datamartManager.getWeatherSeries(icao, limit));
        });

        app.get("/api/weather-records", ctx -> {
            int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(500);
            ctx.json(datamartManager.getAllWeatherRecords(limit));
        });

        app.get("/api/airports", ctx -> ctx.json(datamartManager.getAirports()));

    }
}