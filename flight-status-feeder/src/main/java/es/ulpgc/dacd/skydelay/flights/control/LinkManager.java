package es.ulpgc.dacd.skydelay.flights.control;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class LinkManager {
    private final Path FILE_PATH = Paths.get("pending_flight_links.txt");

    public List<String> getPendingLinks() throws IOException {
        if (!Files.exists(FILE_PATH)) return new ArrayList<>();
        return Files.readAllLines(FILE_PATH);
    }

    public void saveLinks(List<String> links) throws IOException {
        Files.write(FILE_PATH, links, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public void removeProcessedLinks(List<String> processed) throws IOException {
        List<String> current = getPendingLinks();
        current.removeAll(processed);
        saveLinks(current);
    }
}