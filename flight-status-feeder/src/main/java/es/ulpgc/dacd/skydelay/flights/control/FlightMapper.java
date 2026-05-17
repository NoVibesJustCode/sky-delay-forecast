package es.ulpgc.dacd.skydelay.flights.control;
import java.util.regex.*;

public class FlightMapper {
    public static int parseDelay(String text) {
        if (text == null || text.isBlank() || text.toLowerCase().contains("on time")) return 0;
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        int val = Integer.parseInt(digits);
        return text.toLowerCase().contains("early") ? -val : val;
    }

    public static int parseDistance(String text) {
        if (text == null || text.isEmpty()) return 0;
        String firstPart = text.split("/")[0].trim();
        String numeric = firstPart.replaceAll("[^0-9]", "");
        return numeric.isEmpty() ? 0 : Integer.parseInt(numeric);
    }

    public static String extractTimeUTC(String text) {
        if (text == null) return "N/A";
        Matcher utcMatcher = Pattern.compile("(\\d{2}:\\d{2})\\s*UTC").matcher(text);
        if (utcMatcher.find()) return utcMatcher.group(1);
        Matcher fallback = Pattern.compile("(\\d{2}:\\d{2})").matcher(text);
        return fallback.find() ? fallback.group(1) : "N/A";
    }

    public static String cleanAircraft(String text) {
        return (text == null || text.trim().isEmpty()) ? "Unknown" : text.trim();
    }
}