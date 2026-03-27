package es.ulpgc.dacd.skydelay.flights.control;

import java.time.LocalTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ScrapingScheduler {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final FlightPublisher publisher;

    public ScrapingScheduler(FlightPublisher publisher) {
        this.publisher = publisher;
    }

    public void start() {
        Runnable scraperTask = () -> {
            LocalTime now = LocalTime.now();

            if (now.isAfter(LocalTime.of(1, 0)) && now.isBefore(LocalTime.of(6, 0))) {
                System.out.println("[Scheduler] Quiet hours (1:00 - 6:00). Skipping capture cycle...");
                return;
            }

            try {
                System.out.println("\n[Scheduler] 🕒 " + now + " - Starting periodic capture...");
                FlighteraScraper.startCapture(publisher);
                System.out.println("[Scheduler] Cycle completed successfully.");
            } catch (Exception e) {
                System.err.println("[Scheduler] Cycle error: " + e.getMessage());
            }
        };

        System.out.println("[Scheduler] Automated scraping service started (Every 30 min)");
        scheduler.scheduleAtFixedRate(scraperTask, 0, 30, TimeUnit.MINUTES);
    }
}
