package es.ulpgc.dacd.skydelay.weather.model;

public record Weather(
        String airport,
        String description,
        double temp,
        double feelsLike,
        int humidity,
        int visibility,
        double windSpeed,
        double windGust,
        int cloudsPct,
        long timestamp
) {
    @Override
    public String toString() {
        return String.format(
                "Meteorología en %s \n" +
                        "Estado: %s\n" +
                        "Temperatura: %.2f°C (Sensación: %.2f°C)\n" +
                        "Humedad: %d%%\n" +
                        "Visibilidad: %d metros\n" +
                        "Viento: %.2f m/s (Ráfagas: %.2f m/s)\n" +
                        "Porcentaje de nubosidad: %d%%\n",
                airport, description, temp, feelsLike, humidity, visibility, windSpeed, windGust, cloudsPct
        );
    }
}

