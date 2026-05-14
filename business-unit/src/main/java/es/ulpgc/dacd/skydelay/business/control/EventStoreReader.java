package es.ulpgc.dacd.skydelay.business.control;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

public class EventStoreReader {
    private final Path rootPath;
    private final Gson gson;

    public EventStoreReader(String rootPath, Gson gson) {
        this.rootPath = Paths.get(rootPath);
        this.gson = gson;
    }

    public <T> void processEvents(String topic, Class<T> clazz, java.util.function.Consumer<T> processor) {
        Path topicPath = rootPath.resolve(topic);

        if (!Files.exists(topicPath)) {
            System.out.println("Topic folder not found: " + topic);
            return;
        }

        try (Stream<Path> files = Files.walk(topicPath)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".events"))
                    .forEach(file -> {
                        try (Stream<String> lines = Files.lines(file)) {
                            lines.forEach(line -> {
                                T event = gson.fromJson(line, clazz);
                                processor.accept(event);
                            });
                        } catch (IOException e) {
                            System.err.println("Error reading file: " + file + " -> " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            System.err.println("Error walking through Event Store: " + e.getMessage());
        }
    }
}