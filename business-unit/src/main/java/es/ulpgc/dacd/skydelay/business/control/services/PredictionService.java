package es.ulpgc.dacd.skydelay.business.control.services;

import es.ulpgc.dacd.skydelay.business.control.AirportCodeTranslator;
import es.ulpgc.dacd.skydelay.business.control.KNNClassifier;
import es.ulpgc.dacd.skydelay.business.control.datamart.FlightDAO;
import es.ulpgc.dacd.skydelay.business.control.datamart.WeatherDAO;
import es.ulpgc.dacd.skydelay.flights.model.Flight;
import es.ulpgc.dacd.skydelay.weather.model.Weather;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        flightDAO.savePrediction(
                f.flightId(),
                originIcao,
                destIcao,
                f.ts().toString(),
                category
        );

        logger.info("Prediction saved for flight {}: {} (origin: {})",
                f.flightId(), category, originIcao);
    }
}
