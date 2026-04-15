package es.ulpgc.dacd.skydelay.flights.control;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class LinkManager {
    private final Path filePath;

    public LinkManager(String path) {
        this.filePath = Paths.get(path);
    }

    public List<String> getPendingLinks() throws IOException {
        if (!Files.exists(filePath)) return new ArrayList<>();
        return Files.readAllLines(filePath);
    }

    public void saveLinks(List<String> links) throws IOException {
        Files.write(filePath, links, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public void addUniqueLinks(List<String> newLinks) throws IOException {
        Set<String> allLinks = new HashSet<>(getPendingLinks());
        allLinks.addAll(newLinks);
        saveLinks(new ArrayList<>(allLinks));
    }

    public void removeProcessedLinks(List<String> processed) throws IOException {
        List<String> current = getPendingLinks();
        current.removeAll(processed);
        saveLinks(current);
    }
}