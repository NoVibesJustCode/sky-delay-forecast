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

        dashboardApp.get("/api/flight-features", ctx ->
                ctx.json(flightDAO.getAllFlightFeatures()));

        dashboardApp.get("/api/weather-series", ctx -> {
            String icao = ctx.queryParamAsClass("icao", String.class).getOrDefault("LEMD");
            int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(200);
            ctx.json(weatherDAO.getSeries(icao, limit));
        });

        dashboardApp.get("/api/data", ctx -> ctx.json(flightDAO.getReadyToEatMenu()));
        setupCommonRoutes(dashboardApp);

        mapApp.get("/api/data", ctx -> {
            flightDAO.purgeExpiredPredictions();
            ctx.json(mapDataService.getAirportsWithPredictions());
        });

        // Full predictions list (for the predictions page) with optional origin filter
        mapApp.get("/api/predictions", ctx -> {
            String origin = ctx.queryParam("origin");
            ctx.json(flightDAO.getAllPredictions(origin));
        });

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