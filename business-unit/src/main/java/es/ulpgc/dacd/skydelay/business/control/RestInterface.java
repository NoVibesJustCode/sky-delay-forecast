package es.ulpgc.dacd.skydelay.business.control;

import io.javalin.Javalin;

public class RestInterface {
    private final DatamartManager datamart;
    private final int port;

    public RestInterface(DatamartManager datamart, int port) {
        this.datamart = datamart;
        this.port = port;
    }

    public void start() {
        Javalin app = Javalin.create(config -> {
            config.bundledPlugins.enableDevLogging();
        });

        app.get("/", ctx -> ctx.result("SkyDelay Forecast API is running!"));

        app.get("/api/analysis", ctx -> {
            ctx.json(datamart.getFlightAnalysis());
        });

        /*app.get("/api/weather", ctx -> {
            ctx.json(datamart.getAllCurrentWeather());
        });
        */

        app.start(port);
    }
}
