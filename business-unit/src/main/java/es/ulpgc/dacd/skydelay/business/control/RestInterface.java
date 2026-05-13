package es.ulpgc.dacd.skydelay.business.control;

import es.ulpgc.dacd.skydelay.business.control.datamart.FlightDAO;
import es.ulpgc.dacd.skydelay.business.control.datamart.WeatherDAO;
import es.ulpgc.dacd.skydelay.business.control.services.MapDataService;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

public class RestInterface {

    private final FlightDAO flightDAO;
    private final WeatherDAO weatherDAO;
    private final MapDataService mapDataService;
    private final AirportCodeTranslator translator;

    public RestInterface(FlightDAO flightDAO, WeatherDAO weatherDAO,
                         MapDataService mapDataService, AirportCodeTranslator translator) {
        this.flightDAO      = flightDAO;
        this.weatherDAO     = weatherDAO;
        this.mapDataService = mapDataService;
        this.translator     = translator;
    }

    public void start() {
        Javalin dashboardApp = Javalin.create(config ->
                config.staticFiles.add("/public/dashboard", Location.CLASSPATH)
        ).start(7070);

        Javalin mapApp = Javalin.create(config ->
                config.staticFiles.add("/public/map", Location.CLASSPATH)
        ).start(8080);

        // Dashboard: menú Ready-to-Eat (predicciones ya generadas)
        dashboardApp.get("/api/data", ctx -> ctx.json(flightDAO.getReadyToEatMenu()));
        setupCommonRoutes(dashboardApp);

        // Mapa: aeropuertos con retraso promedio y vuelos recientes
        mapApp.get("/api/data", ctx -> ctx.json(mapDataService.getAirportsWithCurrentDelays()));
        setupCommonRoutes(mapApp);
    }

    private void setupCommonRoutes(Javalin app) {
        app.get("/api/airports", ctx ->
                ctx.json(translator.getAirports())
        );
        app.get("/api/weather-records", ctx -> {
            int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(500);
            ctx.json(weatherDAO.getAllWeatherRecords(limit));
        });
    }
}