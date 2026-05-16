package es.ulpgc.dacd.skydelay.business.control.metrics;

import java.util.*;

public class ClassifierEvaluator {

    private final String[] yTrue;
    private final String[] yPred;
    private final List<String> labels;
    private final int[][] confusionMatrix;

    public ClassifierEvaluator(String[] yTrue, String[] yPred) {
        if (yTrue == null || yPred == null || yTrue.length != yPred.length) {
            throw new IllegalArgumentException("yTrue and yPred must be non-null and of equal length.");
        }
        this.yTrue = yTrue;
        this.yPred = yPred;

        this.labels = List.of("none", "low", "moderate", "severe");
        this.confusionMatrix = buildConfusionMatrix();
    }

    private int[][] buildConfusionMatrix() {
        int n = labels.size();
        int[][] matrix = new int[n][n];
        Map<String, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < n; i++) indexMap.put(labels.get(i), i);

        for (int i = 0; i < yTrue.length; i++) {
            Integer trueIdx = indexMap.get(yTrue[i]);
            Integer predIdx = indexMap.get(yPred[i]);
            if (trueIdx != null && predIdx != null) {
                matrix[trueIdx][predIdx]++;
            }
        }
        return matrix;
    }

    public double getAccuracy() {
        int correct = 0, total = 0;
        for (int i = 0; i < labels.size(); i++) {
            for (int j = 0; j < labels.size(); j++) {
                total += confusionMatrix[i][j];
                if (i == j) correct += confusionMatrix[i][j];
            }
        }
        return total == 0 ? 0.0 : (double) correct / total;
    }

    public double getPrecision(String label) {
        int idx = labels.indexOf(label);
        if (idx < 0) return 0.0;
        int tp = confusionMatrix[idx][idx];
        int colSum = 0;
        for (int i = 0; i < labels.size(); i++) colSum += confusionMatrix[i][idx];
        return colSum == 0 ? 0.0 : (double) tp / colSum;
    }

    public double getRecall(String label) {
        int idx = labels.indexOf(label);
        if (idx < 0) return 0.0;
        int tp = confusionMatrix[idx][idx];
        int rowSum = 0;
        for (int j = 0; j < labels.size(); j++) rowSum += confusionMatrix[idx][j];
        return rowSum == 0 ? 0.0 : (double) tp / rowSum;
    }

    public double getF1(String label) {
        double p = getPrecision(label);
        double r = getRecall(label);
        return (p + r) == 0 ? 0.0 : 2.0 * p * r / (p + r);
    }

    public int getSupport(String label) {
        int idx = labels.indexOf(label);
        if (idx < 0) return 0;
        int rowSum = 0;
        for (int j = 0; j < labels.size(); j++) rowSum += confusionMatrix[idx][j];
        return rowSum;
    }

    public double getMacroF1() {
        double sum = 0;
        int count = 0;
        for (String label : labels) {
            if (getSupport(label) > 0) {
                sum += getF1(label);
                count++;
            }
        }
        return count == 0 ? 0.0 : sum / count;
    }

    public double getWeightedF1() {
        double sum = 0;
        int total = 0;
        for (String label : labels) {
            int support = getSupport(label);
            sum += getF1(label) * support;
            total += support;
        }
        return total == 0 ? 0.0 : sum / total;
    }


    public int[][] getConfusionMatrix() {
        return confusionMatrix;
    }

    public List<String> getLabels() {
        return labels;
    }

    public int getTotalSamples() {
        return yTrue.length;
    }

    public Map<String, Object> getFullReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("total_samples", getTotalSamples());
        report.put("accuracy", getAccuracy());
        report.put("macro_f1", getMacroF1());
        report.put("weighted_f1", getWeightedF1());

        List<Map<String, Object>> perClass = new ArrayList<>();
        for (String label : labels) {
            Map<String, Object> classReport = new LinkedHashMap<>();
            classReport.put("class", label);
            classReport.put("precision", getPrecision(label));
            classReport.put("recall", getRecall(label));
            classReport.put("f1_score", getF1(label));
            classReport.put("support", getSupport(label));
            perClass.add(classReport);
        }
        report.put("per_class", perClass);

        List<List<Integer>> cm = new ArrayList<>();
        for (int[] row : confusionMatrix) {
            List<Integer> r = new ArrayList<>();
            for (int v : row) r.add(v);
            cm.add(r);
        }
        report.put("confusion_matrix", cm);
        report.put("confusion_labels", labels);

        return report;
    }

    public String toFormattedReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════════\n");
        sb.append("  SKYDELAY KNN CLASSIFIER — EVALUATION REPORT\n");
        sb.append("═══════════════════════════════════════════════════════\n\n");
        sb.append(String.format("  Total Samples:    %d%n", getTotalSamples()));
        sb.append(String.format("  Overall Accuracy: %.4f (%.1f%%)%n", getAccuracy(), getAccuracy() * 100));
        sb.append(String.format("  Macro F1:         %.4f%n", getMacroF1()));
        sb.append(String.format("  Weighted F1:      %.4f%n%n", getWeightedF1()));

        sb.append("───────────────────────────────────────────────────────\n");
        sb.append(String.format("  %-12s %10s %10s %10s %10s%n", "Class", "Precision", "Recall", "F1", "Support"));
        sb.append("───────────────────────────────────────────────────────\n");
        for (String label : labels) {
            sb.append(String.format("  %-12s %10.4f %10.4f %10.4f %10d%n",
                    label, getPrecision(label), getRecall(label), getF1(label), getSupport(label)));
        }
        sb.append("───────────────────────────────────────────────────────\n\n");

        sb.append("  Confusion Matrix (rows=true, cols=predicted):\n\n");
        sb.append(String.format("  %12s", ""));
        for (String l : labels) sb.append(String.format(" %8s", l));
        sb.append("\n");
        for (int i = 0; i < labels.size(); i++) {
            sb.append(String.format("  %12s", labels.get(i)));
            for (int j = 0; j < labels.size(); j++) {
                sb.append(String.format(" %8d", confusionMatrix[i][j]));
            }
            sb.append("\n");
        }
        sb.append("\n═══════════════════════════════════════════════════════\n");
        return sb.toString();
    }
}
