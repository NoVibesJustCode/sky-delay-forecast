package es.ulpgc.dacd.skydelay.business.control.metrics;

import es.ulpgc.dacd.skydelay.business.control.KNNClassifier;
import es.ulpgc.dacd.skydelay.business.control.datamart.FlightHistoricalDAO;
import es.ulpgc.dacd.skydelay.business.control.datamart.WeatherDAO;
import es.ulpgc.dacd.skydelay.business.model.FlightFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ModelEvaluationService {

    private static final Logger logger = LoggerFactory.getLogger(ModelEvaluationService.class);
    private static final int K_FOLDS = 5;
    private static final int KNN_K = 5;

    private final FlightHistoricalDAO historicalDAO;
    private final String reportsDir;
    private final ScheduledExecutorService scheduler;

    private volatile Map<String, Object> latestReport;

    public ModelEvaluationService(FlightHistoricalDAO historicalDAO, String reportsDir) {
        this.historicalDAO = historicalDAO;
        this.reportsDir = reportsDir;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ModelEvaluation");
            t.setDaemon(true);
            return t;
        });
    }

    public void startPeriodicEvaluation(long initialDelayMinutes, long periodHours) {
        scheduler.scheduleAtFixedRate(this::runEvaluation,
                initialDelayMinutes, periodHours * 60, TimeUnit.MINUTES);
        logger.info("Model evaluation scheduled: initial delay {} min, period {} hours.",
                initialDelayMinutes, periodHours);
    }

    public void runEvaluation() {
        try {
            List<FlightFeature> allData = historicalDAO.loadTrainingData();
            if (allData.size() < 20) {
                logger.warn("Not enough training data for evaluation ({} samples). Skipping.", allData.size());
                return;
            }

            logger.info("Starting model evaluation with {} samples ({}-fold cross-validation)...",
                    allData.size(), K_FOLDS);

            List<FlightFeature> shuffled = new ArrayList<>(allData);
            Collections.shuffle(shuffled, new Random(42));

            List<String> allTrue = new ArrayList<>();
            List<String> allPred = new ArrayList<>();

            int foldSize = shuffled.size() / K_FOLDS;

            for (int fold = 0; fold < K_FOLDS; fold++) {
                int testStart = fold * foldSize;
                int testEnd = (fold == K_FOLDS - 1) ? shuffled.size() : testStart + foldSize;

                List<FlightFeature> trainSet = new ArrayList<>();
                List<FlightFeature> testSet = new ArrayList<>();

                for (int i = 0; i < shuffled.size(); i++) {
                    if (i >= testStart && i < testEnd) {
                        testSet.add(shuffled.get(i));
                    } else {
                        trainSet.add(shuffled.get(i));
                    }
                }

                KNNClassifier foldClassifier = new KNNClassifier(trainSet, KNN_K);

                for (FlightFeature test : testSet) {
                    String predicted = foldClassifier.predict(
                            test.temp(), test.wind(), test.gust(), test.vis());
                    allTrue.add(test.category());
                    allPred.add(predicted);
                }
            }

            ClassifierEvaluator evaluator = new ClassifierEvaluator(
                    allTrue.toArray(new String[0]),
                    allPred.toArray(new String[0])
            );

            this.latestReport = evaluator.getFullReport();
            latestReport.put("timestamp", Instant.now().toString());
            latestReport.put("k_folds", K_FOLDS);
            latestReport.put("knn_k", KNN_K);

            String formattedReport = evaluator.toFormattedReport();
            logger.info("Model evaluation complete:\n{}", formattedReport);

            saveReport(formattedReport);

        } catch (Exception e) {
            logger.error("Error during model evaluation: {}", e.getMessage(), e);
        }
    }

    private void saveReport(String report) {
        try {
            Path dir = Path.of(reportsDir);
            Files.createDirectories(dir);

            String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.now());

            Path file = dir.resolve("evaluation_" + timestamp + ".txt");
            Files.writeString(file, report);
            logger.info("Evaluation report saved to: {}", file.toAbsolutePath());
        } catch (IOException e) {
            logger.error("Could not save evaluation report: {}", e.getMessage());
        }
    }

    public Map<String, Object> getLatestReport() {
        return latestReport;
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
