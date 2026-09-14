package io.strato.aiops.application.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiEvaluationMetricsTest {

    @Test
    void calculatesPerCategoryConfusionMetricsAndAbstentionAccuracy() {
        var summary = AiEvaluationMetrics.calculate(List.of(
                new AiEvaluationMetrics.Observation("PROBE", "PROBE", false, false),
                new AiEvaluationMetrics.Observation("PROBE", "TRAFFIC", false, false),
                new AiEvaluationMetrics.Observation("TRAFFIC", "PROBE", false, false),
                new AiEvaluationMetrics.Observation("UNKNOWN", "UNKNOWN", true, true),
                new AiEvaluationMetrics.Observation("UNKNOWN", "PROBE", true, false)
        ));

        var probe = summary.categories().stream()
                .filter(item -> item.category().equals("PROBE"))
                .findFirst().orElseThrow();
        assertThat(probe.truePositive()).isEqualTo(1);
        assertThat(probe.falsePositive()).isEqualTo(2);
        assertThat(probe.falseNegative()).isEqualTo(1);
        assertThat(probe.precision()).isEqualTo(33.3);
        assertThat(probe.recall()).isEqualTo(50.0);
        assertThat(summary.abstentionAccuracy()).isEqualTo(50.0);
        assertThat(summary.sampleCount()).isEqualTo(5);
    }

    @Test
    void returnsInsufficientEvidenceWhenThereAreNoObservations() {
        var summary = AiEvaluationMetrics.calculate(List.of());

        assertThat(summary.state()).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(summary.macroF1()).isZero();
    }
}
