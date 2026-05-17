package es.ulpgc.dacd.skydelay.flights.control;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class LinkManager {
    private final Path filePath;

    public LinkManager(String path) {
        this.filePath = Paths.get(path);
    }

    public List<String> getPendingLinks() throws IOException {
        if (!Files.exists(filePath)) return new ArrayList<>();

        List<String> links = Files.readAllLines(filePath);
        links.sort(Comparator.comparing(this::extractDate));

        return links;
    }

    public void saveUniqueLinks(List<String> newLinks) throws IOException {
        Set<String> allLinks = new HashSet<>(getPendingLinks());
        allLinks.addAll(newLinks);
        overwrite(new ArrayList<>(allLinks));
    }

    public void removeProcessedLinks(List<String> processed) throws IOException {
        List<String> current = getPendingLinks();

        List<String> notRemoved = current.stream()
                .filter(link -> !processed.contains(link))
                .collect(Collectors.toList());

        if (!notRemoved.isEmpty() && !processed.isEmpty()) {
            Collections.rotate(notRemoved, -processed.size());
        }
        overwrite(notRemoved);
    }

    private String extractDate(String link) {
        try {
            return link.substring(link.lastIndexOf('/') + 1);
        } catch (Exception e) {
            return "9999-12-31";
        }
    }

    private void overwrite(List<String> links) throws IOException {
        if (filePath.getParent() != null) {
            Files.createDirectories(filePath.getParent());
        }
        Files.write(filePath, links, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}