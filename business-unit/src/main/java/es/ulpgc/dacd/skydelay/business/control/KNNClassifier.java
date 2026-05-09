package es.ulpgc.dacd.skydelay.business.control;

import es.ulpgc.dacd.skydelay.business.model.FlightFeature;

import java.util.*;
import java.util.stream.Collectors;

public class KNNClassifier {
    private final List<FlightFeature> trainingData;
    private final int k;


    private final double minTemp = -10, maxTemp = 45;
    private final double minWind = 0, maxWind = 100;
    private final double minGust = 0, maxGust = 150;
    private final double minVis = 0, maxVis = 10000;

    public KNNClassifier(List<FlightFeature> trainingData, int k) {
        this.trainingData = trainingData;
        this.k = k;
    }

    public String predict(double temp, double wind, double gust, double vis) {
        if (trainingData.isEmpty()) return "none";

        double nTemp = normalize(temp, minTemp, maxTemp);
        double nWind = normalize(wind, minWind, maxWind);
        double nGust = normalize(gust, minGust, maxGust);
        double nVis = normalize(vis, minVis, maxVis);

        List<Neighbor> neighbors = new ArrayList<>();
        for (FlightFeature record : trainingData) {
            double distance = calculateEuclideanDistance(
                    nTemp, nWind, nGust, nVis,
                    normalize(record.temp(), minTemp, maxTemp),
                    normalize(record.wind(), minWind, maxWind),
                    normalize(record.gust(), minGust, maxGust),
                    normalize(record.vis(), minVis, maxVis)
            );
            neighbors.add(new Neighbor(record.category(), distance));
        }

        return neighbors.stream()
                .sorted(Comparator.comparingDouble(Neighbor::distance))
                .limit(k)
                .map(Neighbor::category)
                .collect(Collectors.groupingBy(cat -> cat, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("none");
    }

    private double normalize(double val, double min, double max) {
        if (max == min) return 0.0;
        double normalized = (val - min) / (max - min);
        return Math.max(0.0, Math.min(1.0, normalized));
    }

    private double calculateEuclideanDistance(double t1, double w1, double g1, double v1,
                                              double t2, double w2, double g2, double v2) {
        return Math.sqrt(
                        Math.pow(t1 - t2, 2) +
                        Math.pow(w1 - w2, 2) +
                        Math.pow(g1 - g2, 2) +
                        Math.pow(v1 - v2, 2)
        );
    }

    private record Neighbor(String category, double distance) {}
}