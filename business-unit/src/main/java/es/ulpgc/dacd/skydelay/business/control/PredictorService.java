package es.ulpgc.dacd.skydelay.business.control;

import es.ulpgc.dacd.skydelay.flights.model.Flight;
import es.ulpgc.dacd.skydelay.weather.model.Weather;

public class PredictorService {
    private final DatamartManager datamart;
    private KNNClassifier knn;

    public PredictorService(DatamartManager datamart) {
        this.datamart = datamart;
        refreshModel();
    }

    public void refreshModel() {
        this.knn = new KNNClassifier(datamart.loadTrainingData(), 5);
    }

    public void processNewFlight(Flight f) {
        Weather forecast = datamart.fetchClosestWeather(f.origin(), f.departureTimeUTC());

        if (forecast != null) {
            String category = knn.predict(forecast.temp(), forecast.windSpeed(), forecast.windGust(), forecast.visibility());
            datamart.saveReadyToEatPrediction(f, category);
        }
    }
}