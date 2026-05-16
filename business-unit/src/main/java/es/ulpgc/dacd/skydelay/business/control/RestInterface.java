package es.ulpgc.dacd.skydelay.business.control;

import es.ulpgc.dacd.skydelay.business.control.datamart.FlightHistoricalDAO;
import es.ulpgc.dacd.skydelay.business.control.datamart.FlightPredictionsDAO;
import es.ulpgc.dacd.skydelay.business.control.datamart.WeatherDAO;
import es.ulpgc.dacd.skydelay.business.control.services.MapDataService;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public class RestInterface {

    private static final Logger logger = LoggerFactory.getLogger(RestInterface.class);

    private final FlightHistoricalDAO historicalDAO;
    private final FlightPredictionsDAO predictionsDAO;
    private final WeatherDAO weatherDAO;
    private final MapDataService mapDataService;
    private final AirportCodeTranslator translator;

    public RestInterface(FlightHistoricalDAO historicalDAO, FlightPredictionsDAO predictionsDAO,
                         WeatherDAO weatherDAO, MapDataService mapDataService,
                         AirportCodeTranslator translator) {
        this.historicalDAO   = historicalDAO;
        this.predictionsDAO  = predictionsDAO;
        this.weatherDAO      = weatherDAO;
        this.mapDataService  = mapDataService;
        this.translator      = translator;
    }

    private static String resolveWebPath(String subDir) {
        String[] candidates = {
                "web/" + subDir,
                "../web/" + subDir,
                "../../web/" + subDir,
        };
        for (String candidate : candidates) {
            if (Files.isDirectory(Path.of(candidate))) {
                logger.info("Serving '{}' from external path: {}", subDir, Path.of(candidate).toAbsolutePath());
                return candidate;
            }
        }
        return null;
    }

    public void start() {
        String dashboardPath = resolveWebPath("dashboard");
        String mapPath = resolveWebPath("map");

        Javalin dashboardApp = Javalin.create(config -> {
            if (dashboardPath != null) {
                config.staticFiles.add(dashboardPath, Location.EXTERNAL);
            }
        }).start(7070);

        Javalin mapApp = Javalin.create(config -> {
            if (mapPath != null) {
                config.staticFiles.add(mapPath, Location.EXTERNAL);
            }
        }).start(8080);

        dashboardApp.get("/api/flight-features", ctx ->
                ctx.json(historicalDAO.getAllFlightFeatures()));

        dashboardApp.get("/api/weather-series", ctx -> {
            String icao = ctx.queryParamAsClass("icao", String.class).getOrDefault("LEMD");
            int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(200);
            ctx.json(weatherDAO.getSeries(icao, limit));
        });

        dashboardApp.get("/api/data", ctx -> ctx.json(predictionsDAO.getReadyToEatMenu()));
        setupCommonRoutes(dashboardApp);

        mapApp.get("/api/data", ctx -> {
            predictionsDAO.purgeExpiredPredictions();
            ctx.json(mapDataService.getAirportsWithPredictions());
        });

        mapApp.get("/api/predictions", ctx -> {
            String origin = ctx.queryParam("origin");
            ctx.json(predictionsDAO.getAllPredictions(origin));
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