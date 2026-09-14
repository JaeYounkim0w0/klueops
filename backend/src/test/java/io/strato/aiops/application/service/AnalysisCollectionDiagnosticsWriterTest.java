package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnostics;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisCollectionDiagnosticsWriterTest {

    @Test
    void marksPartialCollectionAndCapsConfidence() {
        ObjectNode root = new ObjectMapper().createObjectNode().put("confidence", 0.82);
        KubernetesNamespaceDiagnostics diagnostics = new KubernetesNamespaceDiagnostics(
                List.of(), List.of(), List.of(), Instant.EPOCH,
                List.of(
                        new KubernetesNamespaceDiagnostics.CollectionStage("pods", "SUCCEEDED", 3, 12, null),
                        new KubernetesNamespaceDiagnostics.CollectionStage("events", "FAILED", 0, 45, "forbidden")
                ));

        new AnalysisCollectionDiagnosticsWriter().write(root, diagnostics);

        assertThat(root.path("confidence").asDouble()).isEqualTo(0.5);
        assertThat(root.path("analysisDiagnostics").path("collection").path("status").asText())
                .isEqualTo("PARTIAL");
        assertThat(root.path("analysisDiagnostics").path("collection").path("stages")).hasSize(2);
    }

    @Test
    void leavesLegacyDiagnosticsUntouchedWhenStageMetadataIsUnavailable() {
        ObjectNode root = new ObjectMapper().createObjectNode().put("confidence", 0.72);

        new AnalysisCollectionDiagnosticsWriter().write(root,
                new KubernetesNamespaceDiagnostics(List.of(), List.of(), List.of(), Instant.EPOCH));

        assertThat(root.has("analysisDiagnostics")).isFalse();
        assertThat(root.path("confidence").asDouble()).isEqualTo(0.72);
    }
}
