package es.ulpgc.dacd.skydelay.business.control;

public interface Classifier {

    String predict(double temp, double wind, double gust, double vis);

    double normalize(double val, double min, double max);
}