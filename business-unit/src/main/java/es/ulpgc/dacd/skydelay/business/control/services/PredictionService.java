package es.ulpgc.dacd.skydelay.business.control.services;

import es.ulpgc.dacd.skydelay.business.control.AirportCodeTranslator;
import es.ulpgc.dacd.skydelay.business.control.KNNClassifier;
import es.ulpgc.dacd.skydelay.business.control.datamart.FlightHistoricalDAO;
import es.ulpgc.dacd.skydelay.business.control.datamart.FlightPredictionsDAO;
import es.ulpgc.dacd.skydelay.business.control.datamart.WeatherDAO;
import es.ulpgc.dacd.skydelay.flights.model.Flight;
import es.ulpgc.dacd.skydelay.weather.model.Weather;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Locale;

public class PredictionService {

    private static final Logger logger = LoggerFactory.getLogger(PredictionService.class);

    private static final int KNN_K = 5;

    private final FlightHistoricalDAO historicalDAO;
    private final FlightPredictionsDAO predictionsDAO;
    private final WeatherDAO weatherDAO;
    private final AirportCodeTranslator translator;
    private KNNClassifier knn;

    public PredictionService(FlightHistoricalDAO historicalDAO, FlightPredictionsDAO predictionsDAO,
                             WeatherDAO weatherDAO, AirportCodeTranslator translator) {
        this.historicalDAO  = historicalDAO;
        this.predictionsDAO = predictionsDAO;
        this.weatherDAO     = weatherDAO;
        this.translator     = translator;
        refreshModel();
    }

    public void refreshModel() {
        this.knn = new KNNClassifier(historicalDAO.loadTrainingData(), KNN_K);
        logger.info("KNN model refreshed ({} training samples).",
                historicalDAO.loadTrainingData().size());
    }

    public void processNewFlight(Flight f) {
        String originIcao = translator.toIcao(f.origin());
        Weather forecast  = weatherDAO.findClosest(originIcao, f.ts().toString());

        if (forecast == null) {
            logger.warn("No weather data found for {} at {}. Skipping prediction.",
                    originIcao, f.ts());
            return;
        }

        String category = knn.predict(
                forecast.temp(),
                forecast.windSpeed(),
                forecast.windGust(),
                forecast.visibility()
        );

        String destIcao = translator.toIcao(f.destination());
        String scheduledDeparture = buildScheduledDeparture(f);

        try {
            Instant scheduledInstant = Instant.parse(scheduledDeparture);
            if (scheduledInstant.isBefore(Instant.now().minus(Duration.ofHours(1)))) {
                logger.warn("Skipping prediction for {}: scheduled time {} is in the past.",
                        f.flightId(), scheduledDeparture);
                return;
            }
        } catch (DateTimeParseException e) {
            logger.warn("Could not validate scheduled time '{}' for {}.", scheduledDeparture, f.flightId());
        }

        predictionsDAO.savePrediction(
                f.flightId(),
                originIcao,
                destIcao,
                scheduledDeparture,
                category
        );

        logger.info("Prediction saved for flight {}: {} (origin: {}, scheduled: {})",
                f.flightId(), category, originIcao, scheduledDeparture);
    }

    private String buildScheduledDeparture(Flight f) {
        String depTimeStr = f.departureTimeUTC();

        if (depTimeStr == null || "N/A".equals(depTimeStr) || depTimeStr.isBlank()) {
            logger.warn("No UTC departure time for {}. Using scrape timestamp.", f.flightId());
            return f.ts().toString();
        }

        try {
            LocalTime depTime = LocalTime.parse(depTimeStr);

            LocalDate depDate = tryParseFlightDate(f.date());
            if (depDate == null) {
                depDate = f.ts().atZone(ZoneOffset.UTC).toLocalDate();
            }

            return LocalDateTime.of(depDate, depTime)
                    .toInstant(ZoneOffset.UTC)
                    .toString();
        } catch (DateTimeParseException e) {
            logger.warn("Could not parse departure time '{}' for {}. Using scrape timestamp.",
                    depTimeStr, f.flightId());
            return f.ts().toString();
        }
    }

    private static LocalDate tryParseFlightDate(String dateText) {
        if (dateText == null || dateText.isBlank()) return null;

        try {
            return LocalDate.parse(dateText.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH));
        } catch (DateTimeParseException ignored) { }

        String cleaned = dateText.replaceAll("^[a-zA-Z]{3,9},?\\s*", "").trim();

        cleaned = cleaned.replaceAll("\\d{1,2}:\\d{2}(:\\d{2})?\\s*(AM|PM|am|pm)?\\s*\\w{0,4}$", "").trim();

        cleaned = cleaned.replaceAll(",\\s*$", "").trim();

        cleaned = cleaned.replaceAll("^(\\d{1,2})\\.\\s*", "$1 ").trim();

        DateTimeFormatter[] formats = {
                DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH),
                new DateTimeFormatterBuilder()
                        .appendPattern("d MMM")
                        .parseDefaulting(ChronoField.YEAR, Year.now().getValue())
                        .toFormatter(Locale.ENGLISH),
                new DateTimeFormatterBuilder()
                        .appendPattern("MMM d")
                        .parseDefaulting(ChronoField.YEAR, Year.now().getValue())
                        .toFormatter(Locale.ENGLISH),
        };

        for (DateTimeFormatter fmt : formats) {
            try {
                return LocalDate.parse(cleaned, fmt);
            } catch (DateTimeParseException ignored) { }
        }
        return null;
    }

    public void saveHistoricalFlight(Flight f) {
        String originIcao = translator.toIcao(f.origin());
        String destIcao   = translator.toIcao(f.destination());

        Weather w = weatherDAO.findClosest(originIcao, f.ts().toString());
        if (w == null) {
            logger.warn("No weather data for {} at {}. Historical flight {} skipped.",
                    originIcao, f.ts(), f.flightId());
            return;
        }

        String scheduledDep = buildScheduledDeparture(f);

        historicalDAO.saveFeature(
                f.flightId(),
                originIcao,
                destIcao,
                w.temp(),
                w.windSpeed(),
                w.windGust(),
                w.visibility(),
                f.distanceKm(),
                f.departureDelay(),
                FlightHistoricalDAO.categorize(f.departureDelay()),
                scheduledDep,
                f.aircraftModel()
        );

        logger.info("Historical flight {} saved (origin: {}, delay: {} min, category: {}).",
                f.flightId(), originIcao, f.departureDelay(),
                FlightHistoricalDAO.categorize(f.departureDelay()));
    }
}
