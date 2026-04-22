package es.ulpgc.dacd.skydelay.eventstore.control;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class FileEventStore {
    private static final Logger logger = LoggerFactory.getLogger(FileEventStore.class);
    private final Path root;
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("UTC"));


    public FileEventStore(String rootPath) {
        this.root = Paths.get(rootPath);
    }

    public void save(String topic, String json) {
        try {
            JsonObject event = JsonParser.parseString(json).getAsJsonObject();

            Path filePath = generatePath(topic, event);

            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, json + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            logger.debug("Event successfully stored in: {}", filePath);
        } catch (Exception e) {
            logger.error("Could not store event for topic {}: {}", topic, e.getMessage());
        }
    }

    private Path generatePath(String topic, JsonObject event) {
        String ss = event.get("ss").getAsString();
        String ts = event.get("ts").getAsString();
        String date = DATE_FORMATTER.format(Instant.parse(ts));

        return root.resolve(topic).resolve(ss).resolve(date + ".events");
    }
}