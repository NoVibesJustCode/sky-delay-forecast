package es.ulpgc.dacd.skydelay.business.control;

import es.ulpgc.dacd.skydelay.business.control.datamart.FlightHistoricalDAO;
import es.ulpgc.dacd.skydelay.business.control.datamart.FlightPredictionsDAO;
import es.ulpgc.dacd.skydelay.business.control.datamart.WeatherDAO;
import es.ulpgc.dacd.skydelay.business.control.metrics.ModelEvaluationService;
import es.ulpgc.dacd.skydelay.business.control.services.MapDataService;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class RestInterface {

    private static final Logger logger = LoggerFactory.getLogger(RestInterface.class);

    private final FlightHistoricalDAO historicalDAO;
    private final FlightPredictionsDAO predictionsDAO;
    private final WeatherDAO weatherDAO;
    private final MapDataService mapDataService;
    private final AirportCodeTranslator translator;
    private final ModelEvaluationService evaluationService;

    public RestInterface(FlightHistoricalDAO historicalDAO, FlightPredictionsDAO predictionsDAO,
                         WeatherDAO weatherDAO, MapDataService mapDataService,
                         AirportCodeTranslator translator, ModelEvaluationService evaluationService) {
        this.historicalDAO      = historicalDAO;
        this.predictionsDAO     = predictionsDAO;
        this.weatherDAO         = weatherDAO;
        this.mapDataService     = mapDataService;
        this.translator         = translator;
        this.evaluationService  = evaluationService;
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
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));
            if (dashboardPath != null) {
                config.staticFiles.add(dashboardPath, Location.EXTERNAL);
            }
        }).start(9090);

        Javalin mapApp = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));
            if (mapPath != null) {
                config.staticFiles.add(mapPath, Location.EXTERNAL);
            }
        }).start(8080);

        Javalin launcherApp = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/launcher";
                staticFiles.location = Location.CLASSPATH;
            });
        }).start(7070);

        launcherApp.get("/docs/user_guide.pdf", ctx -> servePdf(ctx));

        launcherApp.get("/assets/logo.png",             ctx -> serveLogo(ctx, "skydelay_logo.png"));
        launcherApp.get("/assets/logo_transparent.png", ctx -> serveLogo(ctx, "skydelay_logo_transparent.png"));

        launcherApp.post("/api/shutdown", ctx -> {
            logger.info("Shutdown requested via launcher.");
            ctx.json(Map.of("status", "shutting-down"));
            new Thread(() -> {
                try { Thread.sleep(400); } catch (InterruptedException ignored) {}
                logger.info("SkyDelay shutting down. Goodbye.");
                System.exit(0);
            }, "shutdown-thread").start();
        });

        logger.info("Mission Control launcher live at http://localhost:7070");
        logger.info("Public flight map live at http://localhost:8080");
        logger.info("Business dashboard live at http://localhost:9090");
        openBrowser("http://localhost:7070");

        dashboardApp.get("/api/flight-features", ctx ->
                ctx.json(historicalDAO.getAllFlightFeatures()));

        dashboardApp.get("/api/weather-series", ctx -> {
            String icao = ctx.queryParamAsClass("icao", String.class).getOrDefault("LEMD");
            int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(200);
            ctx.json(weatherDAO.getSeries(icao, limit));
        });

        dashboardApp.get("/api/data", ctx -> ctx.json(predictionsDAO.getReadyToEatMenu()));

        dashboardApp.get("/api/model-evaluation", ctx -> {
            Map<String, Object> report = evaluationService.getLatestReport();
            if (report != null) ctx.json(report);
            else ctx.status(204).result("No evaluation available yet.");
        });

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

        app.get("/assets/logo.png", ctx -> serveLogo(ctx, "skydelay_logo.png"));
        app.get("/assets/logo_transparent.png", ctx -> serveLogo(ctx, "skydelay_logo_transparent.png"));
    }

    private void serveLogo(io.javalin.http.Context ctx, String filename) throws IOException {
        Path logo = resolveAssetPath(filename);
        if (logo != null && Files.exists(logo)) {
            ctx.contentType("image/png");
            ctx.result(Files.newInputStream(logo));
        } else {
            ctx.status(404).result("Logo not found");
        }
    }

    private void servePdf(io.javalin.http.Context ctx) throws IOException {
        Path pdf = resolvePdfPath();
        if (pdf != null && Files.exists(pdf)) {
            ctx.contentType("application/pdf");
            ctx.header("Content-Disposition", "inline; filename=\"SkyDelay-User-Guide.pdf\"");
            ctx.result(Files.newInputStream(pdf));
        } else {
            ctx.status(404).result("User guide not found");
        }
    }

    private static Path resolveAssetPath(String filename) {
        String[] prefixes = { "docs/assets/", "../docs/assets/", "../../docs/assets/" };
        for (String prefix : prefixes) {
            Path p = Path.of(prefix + filename);
            if (Files.exists(p)) return p;
        }
        return null;
    }

    private static Path resolvePdfPath() {
        String[] candidates = {
                "docs/user-guide/user_guide.pdf",
                "../docs/user-guide/user_guide.pdf",
                "../../docs/user-guide/user_guide.pdf"
        };
        for (String c : candidates) {
            Path p = Path.of(c);
            if (Files.exists(p)) return p;
        }
        return null;
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                logger.info("Browser opened at {}", url);
                return;
            }
        } catch (Exception e) {
            logger.warn("Could not open browser via Desktop API: {}", e.getMessage());
        }
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", "start", "", url);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", url);
            } else {
                pb = new ProcessBuilder("xdg-open", url);
            }
            pb.start();
            logger.info("Browser launched via OS command at {}", url);
        } catch (Exception e) {
            logger.warn("Could not auto-open browser: {} — please open {} manually.", e.getMessage(), url);
        }
    }
}