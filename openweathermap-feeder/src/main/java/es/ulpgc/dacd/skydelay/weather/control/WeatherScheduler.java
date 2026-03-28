package es.ulpgc.dacd.skydelay.weather.control;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WeatherScheduler {
    private final Control control;
    private ScheduledExecutorService scheduler;

    public WeatherScheduler(Control control) {
        this.control = control;
    }

    public void start(long period, TimeUnit unit) {
        if (scheduler != null && !scheduler.isShutdown()) {
            System.out.println("La captura de datos periódica está en ejecución.");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor();

        Runnable task = () -> {
            System.out.println("\nIniciando captura de datos programada.");
            try {
                control.execute();
                System.out.println("Datos guardados correctamente. Siguiente ejecución en " + period + " " + unit.toString().toLowerCase() + ".");
            } catch (Exception e) {
                System.err.println("Error durante la captura automática de datos: " + e.getMessage());
            }
            System.out.print("> ");
        };

        scheduler.scheduleAtFixedRate(task, 0, period, unit);
    }

    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            System.out.println("Se ha detenido la captura automática de datos.");
        }
    }
}