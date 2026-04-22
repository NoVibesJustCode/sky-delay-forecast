package es.ulpgc.dacd.skydelay.eventstore.control;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class EventsFilePathGenerator {
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("UTC"));

    public static Path generate(Path root, String topic, JsonObject event) {
        String ss = event.get("ss").getAsString();
        String ts = event.get("ts").getAsString();
        String date = DATE_FORMATTER.format(Instant.parse(ts));
        return root.resolve(topic).resolve(ss).resolve(date + ".events");
    }
}
