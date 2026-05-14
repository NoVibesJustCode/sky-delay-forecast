package es.ulpgc.dacd.skydelay.business.control;

public interface Classifier {

    String predict(double feature1, double feature2, double feature3, double feature4);

    double normalize(double value, double min, double max);
}