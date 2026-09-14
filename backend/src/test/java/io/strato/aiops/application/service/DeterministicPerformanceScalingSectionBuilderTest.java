package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicPerformanceScalingSectionBuilderTest {

    private final DeterministicPerformanceScalingSectionBuilder builder =
            new DeterministicPerformanceScalingSectionBuilder(new ObjectMapper());

    @Test
    void buildsPerformanceAndScalingContractFromBoundedSignals() {
        var input = new DeterministicPerformanceScalingSectionBuilder.Input(
                true, false, true, false, 2, 1,
                1, 1, 0,
                List.of(new DeterministicPerformanceScalingSectionBuilder.Signal(
                        "Pod", "web-0", "Pending", "inspect scheduling")),
                List.of(new DeterministicPerformanceScalingSectionBuilder.Signal(
                        "Pod", "web-0", "FailedScheduling count=2", "increase node capacity")),
                List.of(new DeterministicPerformanceScalingSectionBuilder.Signal(
                        "Pod", "web-0", "FailedScheduling", "increase node capacity")));

        ObjectNode result = builder.build(input);

        assertThat(result.path("_sectionName").asText()).isEqualTo("performance-scaling");
        assertThat(result.path("_sectionSource").asText()).isEqualTo("DETERMINISTIC");
        assertThat(result.path("performance").path("bottlenecks").get(0).path("resourceName").asText())
                .isEqualTo("web-0");
        assertThat(result.path("scaling").path("scaleUpCandidates").get(1).path("resourceKind").asText())
                .isEqualTo("NodeCapacity");
        assertThat(result.path("scaling").path("capacityNotes").get(0).asText())
                .isEqualTo("Pending pods=2, endpointReadyIssues=1.");
    }

    @Test
    void suppliesSafeFallbackSignalsWhenNoBottleneckOrScaleCandidateExists() {
        ObjectNode result = builder.build(new DeterministicPerformanceScalingSectionBuilder.Input(
                false, false, false, false, 0, 0, 0, 0, 0, List.of(), List.of(), List.of()));

        assertThat(result.path("performance").path("bottlenecks").get(0).path("resourceKind").asText())
                .isEqualTo("Namespace");
        assertThat(result.path("scaling").path("scaleUpCandidates").get(0).path("resourceKind").asText())
                .isEqualTo("Namespace");
        assertThat(result.path("scaling").path("hpaRecommendations")).hasSize(1);
    }
}
