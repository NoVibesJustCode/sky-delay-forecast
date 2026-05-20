package es.ulpgc.dacd.skydelay.flights.control;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;

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

    public void removeProcessedLinks(List<String> processed, List<String> attempted) throws IOException {
        List<String> current = getPendingLinks();

        Set<String> processedSet = new HashSet<>(processed);
        Set<String> toRotate = new HashSet<>(attempted);
        toRotate.removeAll(processedSet);

        List<String> remaining = current.stream()
                .filter(link -> !processedSet.contains(link))
                .toList();

        LinkedHashMap<String, List<String>> byDate = new LinkedHashMap<>();
        for (String link : remaining) {
            byDate.computeIfAbsent(extractDate(link), k -> new ArrayList<>()).add(link);
        }

        List<String> reordered = new ArrayList<>();
        for (List<String> dateGroup : byDate.values()) {
            List<String> stay = dateGroup.stream().filter(l -> !toRotate.contains(l)).toList();
            List<String> rotate = dateGroup.stream().filter(toRotate::contains).toList();
            reordered.addAll(stay);
            reordered.addAll(rotate);
        }

        overwrite(reordered);
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