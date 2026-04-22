package es.ulpgc.dacd.skydelay.eventstore.control;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.*;

public class FileEventStore {
    private static final Logger logger = LoggerFactory.getLogger(FileEventStore.class);
    private final Path root;

    public FileEventStore(String rootPath) {
        this.root = Paths.get(rootPath);
    }

    public void save(String topic, String json) {
        try {
            JsonObject event = JsonParser.parseString(json).getAsJsonObject();

            Path filePath = EventsFilePathGenerator.generate(root, topic, event);

            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, json + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            logger.debug("Event successfully stored in: {}", filePath);
        } catch (Exception e) {
            logger.error("Could not store event for topic {}: {}", topic, e.getMessage());
        }
    }
}