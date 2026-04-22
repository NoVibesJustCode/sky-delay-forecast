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

    public FileEventStore(String rootPath) {
        this.root = Paths.get(rootPath);
    }

    public void save(String topic, String json) {
        try {
            JsonObject event = JsonParser.parseString(json).getAsJsonObject();
            String ss = event.get("ss").getAsString();
            String ts = event.get("ts").getAsString();

            String date = Instant.parse(ts)
                    .atZone(ZoneId.of("UTC"))
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            Path filePath = root.resolve(topic).resolve(ss).resolve(date + ".events");

            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, json + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            logger.debug("Event stored in {}", filePath);
        } catch (Exception e) {
            logger.error("Failed to store event from topic {}: {}", topic, e.getMessage());
        }
    }
}