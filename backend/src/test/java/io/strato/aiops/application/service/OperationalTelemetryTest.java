package io.strato.aiops.application.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalTelemetryTest {

    @Test
    void recordsOnlyBoundedOperationAndOutcomeTags() {
        var registry = new SimpleMeterRegistry();
        var telemetry = new OperationalTelemetry(registry);

        telemetry.record("/api/clusters/2fffd2f8-1d61-4f06-a22e-a55ad65aca1f/resources", "500-secret", Duration.ofMillis(40));

        var snapshot = telemetry.snapshot();
        assertThat(snapshot.series()).singleElement().satisfies(series -> {
            assertThat(series.operation()).isEqualTo("cluster-resource");
            assertThat(series.outcome()).isEqualTo("server-error");
            assertThat(series.count()).isEqualTo(1);
        });
    }

    @Test
    void recordsBoundedAnalysisSectionsAndP95Latency() {
        var telemetry = new OperationalTelemetry(new SimpleMeterRegistry());

        telemetry.recordAnalysisSection("root-cause", "success", Duration.ofMillis(10));
        telemetry.recordAnalysisSection("root-cause", "success", Duration.ofMillis(90));
        telemetry.recordAnalysisSection("cluster-root-cause", "success", Duration.ofMillis(120));
        telemetry.recordAnalysisSection("tenant-secret-value", "timeout", Duration.ofMillis(200));

        assertThat(telemetry.snapshot().series()).anySatisfy(series -> {
            assertThat(series.operation()).isEqualTo("analysis-root-cause");
            assertThat(series.p95Ms()).isEqualTo(90);
        }).anySatisfy(series -> assertThat(series.operation()).isEqualTo("analysis-other-section"));
        assertThat(telemetry.snapshot().series()).anySatisfy(series ->
                assertThat(series.operation()).isEqualTo("analysis-cluster-root-cause"));
    }
}
