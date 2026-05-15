package es.ulpgc.dacd.skydelay.business.control.services;

import es.ulpgc.dacd.skydelay.business.control.AirportCodeTranslator;
import es.ulpgc.dacd.skydelay.business.control.KNNClassifier;
import es.ulpgc.dacd.skydelay.business.control.datamart.FlightDAO;
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

    private final FlightDAO flightDAO;
    private final WeatherDAO weatherDAO;
    private final AirportCodeTranslator translator;
    private KNNClassifier knn;

    public PredictionService(FlightDAO flightDAO, WeatherDAO weatherDAO,
                             AirportCodeTranslator translator) {
        this.flightDAO  = flightDAO;
        this.weatherDAO = weatherDAO;
        this.translator = translator;
        refreshModel();
    }

    public void refreshModel() {
        this.knn = new KNNClassifier(flightDAO.loadTrainingData(), KNN_K);
        logger.info("KNN model refreshed ({} training samples).",
                flightDAO.loadTrainingData().size());
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

        flightDAO.savePrediction(
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
                depDate = LocalDate.now(ZoneOffset.UTC);
                LocalDateTime tentative = LocalDateTime.of(depDate, depTime);
                if (tentative.isBefore(LocalDateTime.now(ZoneOffset.UTC).minusHours(6))) {
                    depDate = depDate.plusDays(1);
                }
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

    /**
     * Attempts to parse the raw date text from the scraper into a LocalDate.
     * Handles several common Flightera formats like "Thu, 15 May 2026", "May 15, 2026",
     * "15 May 2026", etc. Returns null if parsing fails.
     */
    private static LocalDate tryParseFlightDate(String dateText) {
        if (dateText == null || dateText.isBlank()) return null;

        // Strip leading day-of-week names (e.g., "Thu, 15 May" → "15 May")
        String cleaned = dateText.replaceAll("^\\w{3},?\\s*", "").trim();

        DateTimeFormatter[] formats = {
                DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH),
                // Without year — default to current year
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

    /**
     * Persiste un vuelo histórico (ya aterrizado/cancelado) como dato de
     * entrenamiento en {@code flight_features}.
     * Traduce los códigos IATA a ICAO, busca el registro meteorológico más
     * cercano en el tiempo y categoriza el retraso antes de guardar.
     *
     * @param f vuelo con estado LIVE / LANDED / CANCELLED
     */
    public void saveHistoricalFlight(Flight f) {
        String originIcao = translator.toIcao(f.origin());
        String destIcao   = translator.toIcao(f.destination());

        Weather w = weatherDAO.findClosest(originIcao, f.ts().toString());
        if (w == null) {
            logger.warn("No weather data for {} at {}. Historical flight {} skipped.",
                    originIcao, f.ts(), f.flightId());
            return;
        }

        flightDAO.saveFeature(
                f.flightId(),
                originIcao,
                destIcao,
                w.temp(),
                w.windSpeed(),
                w.windGust(),
                w.visibility(),
                f.distanceKm(),
                f.departureDelay(),
                FlightDAO.categorize(f.departureDelay())
        );

        logger.info("Historical flight {} saved (origin: {}, delay: {} min, category: {}).",
                f.flightId(), originIcao, f.departureDelay(),
                FlightDAO.categorize(f.departureDelay()));
    }
}
