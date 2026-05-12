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

        app.get("/api/data", ctx -> {
            var data = datamartManager.getReadyToEatMenu();
            ctx.json(data);
        });

        System.out.println("✈️ SkyDelay Web Dashboard: http://localhost:7070");
    }
}
