package es.ulpgc.dacd.skydelay.flights.control;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class LinkManager {
    private final Path path = Paths.get("pending_flight_links.txt");

    public List<String> getPendingLinks() throws IOException {
        if (!Files.exists(path)) return new ArrayList<>();
        return Files.readAllLines(path);
    }

    public void saveLinks(List<String> links) throws IOException {
        Files.write(path, links, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public void removeProcessedLinks(List<String> processed) throws IOException {
        List<String> current = getPendingLinks();
        current.removeAll(processed);
        saveLinks(current);
    }
}