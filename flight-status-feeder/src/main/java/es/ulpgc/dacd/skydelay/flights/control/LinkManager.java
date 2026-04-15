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

    public void saveUniqueLinks(List<String> newLinks) throws IOException {
        Set<String> allLinks = new HashSet<>(getPendingLinks());
        allLinks.addAll(newLinks);
        overwrite(new ArrayList<>(allLinks));
    }

    public void removeProcessedLinks(List<String> processed) throws IOException {
        List<String> current = getPendingLinks();
        current.removeAll(processed);
        overwrite(current);
    }

    private void overwrite(List<String> links) throws IOException {
        if (filePath.getParent() != null) {
            Files.createDirectories(filePath.getParent());
        }
        Files.write(filePath, links, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}