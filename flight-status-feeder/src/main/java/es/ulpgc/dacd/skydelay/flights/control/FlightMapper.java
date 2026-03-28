package es.ulpgc.dacd.skydelay.flights.control;
import java.util.regex.*;

public class FlightMapper {

    public static int parseDelay(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        String clean = text.replace("+", "").trim().replaceAll("[^0-9-]", "");
        return (clean.isEmpty() || clean.equals("-")) ? 0 : Integer.parseInt(clean);
    }

    public static int parseDistance(String text) {
        if (text == null || text.isEmpty()) return 0;
        String firstPart = text.split("/")[0].trim();
        String numeric = firstPart.replaceAll("[^0-9]", "");
        return numeric.isEmpty() ? 0 : Integer.parseInt(numeric);
    }

    public static String extractTimeUTC(String text) {
        if (text == null) return "N/A";
        Pattern pattern = Pattern.compile("(\\d{2}:\\d{2})");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "N/A";
    }

    public static String cleanAircraft(String text) {
        return (text == null || text.trim().isEmpty()) ? "Unknown" : text.trim();
    }
}