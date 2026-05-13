package es.ulpgc.dacd.skydelay.business.view;

import es.ulpgc.dacd.skydelay.business.control.DatamartManager;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

public class WebDashboard {
    private final DatamartManager datamartManager;

    public WebDashboard(DatamartManager datamartManager) {
        this.datamartManager = datamartManager;
    }

    public void start() {
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public", Location.CLASSPATH);
        }).start(7070);

        // Predictions menu (legacy)
        app.get("/api/data", ctx -> ctx.json(datamartManager.getReadyToEatMenu()));

        // Real flight features (joined with closest-weather snapshot at ingest time)
        app.get("/api/flight-features", ctx -> ctx.json(datamartManager.getAllFlightFeatures()));

        // Weather time series for a given airport (defaults to LEMD)
        app.get("/api/weather-series", ctx -> {
            String icao = ctx.queryParamAsClass("icao", String.class).getOrDefault("LEMD");
            int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(200);
            ctx.json(datamartManager.getWeatherSeries(icao, limit));
        });

        // Latest weather records across all airports
        app.get("/api/weather-records", ctx -> {
            int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(500);
            ctx.json(datamartManager.getAllWeatherRecords(limit));
        });

        // Airport catalogue (ICAO + IATA + name + coordinates)
        app.get("/api/airports", ctx -> ctx.json(datamartManager.getAirports()));

        System.out.println("✈️ SkyDelay Web Dashboard: http://localhost:7070");
    }
}
