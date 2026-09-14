package io.strato.aiops.application.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AiEvaluationMetrics {

    private AiEvaluationMetrics() {
    }

    public static Summary calculate(List<Observation> observations) {
        if (observations == null || observations.isEmpty()) {
            return new Summary("INSUFFICIENT_EVIDENCE", 0, 0, 0, List.of());
        }
        Set<String> labels = new LinkedHashSet<>();
        observations.forEach(item -> {
            if (!"UNKNOWN".equals(item.expectedCategory())) labels.add(item.expectedCategory());
            if (!"UNKNOWN".equals(item.actualCategory())) labels.add(item.actualCategory());
        });
        List<CategoryScore> categories = new ArrayList<>();
        for (String label : labels) {
            int tp = 0;
            int fp = 0;
            int fn = 0;
            for (Observation item : observations) {
                boolean expected = label.equals(item.expectedCategory());
                boolean actual = label.equals(item.actualCategory());
                if (expected && actual) tp++;
                else if (!expected && actual) fp++;
                else if (expected) fn++;
            }
            double precision = percentage(tp, tp + fp);
            double recall = percentage(tp, tp + fn);
            double f1 = precision + recall == 0 ? 0 : round(2 * precision * recall / (precision + recall));
            categories.add(new CategoryScore(label, tp, fp, fn, precision, recall, f1));
        }
        long abstentionSamples = observations.stream().filter(Observation::expectedAbstention).count();
        long correctAbstentions = observations.stream()
                .filter(Observation::expectedAbstention)
                .filter(Observation::actualAbstention)
                .count();
        double abstentionAccuracy = percentage((int) correctAbstentions, (int) abstentionSamples);
        double macroF1 = categories.isEmpty() ? 0
                : round(categories.stream().mapToDouble(CategoryScore::f1).average().orElse(0));
        return new Summary("MEASURED", observations.size(), macroF1, abstentionAccuracy, List.copyOf(categories));
    }

    private static double percentage(int numerator, int denominator) {
        return denominator == 0 ? 0 : round(numerator * 100.0 / denominator);
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public record Observation(String expectedCategory, String actualCategory,
                              boolean expectedAbstention, boolean actualAbstention) {
    }

    public record CategoryScore(String category, int truePositive, int falsePositive, int falseNegative,
                                double precision, double recall, double f1) {
    }

    public record Summary(String state, int sampleCount, double macroF1, double abstentionAccuracy,
                          List<CategoryScore> categories) {
    }
}
