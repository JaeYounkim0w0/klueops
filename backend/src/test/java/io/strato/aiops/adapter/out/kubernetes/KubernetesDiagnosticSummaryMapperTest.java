package io.strato.aiops.adapter.out.kubernetes;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Quantity;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KubernetesDiagnosticSummaryMapperTest {
    private final KubernetesDiagnosticSummaryMapper mapper = new KubernetesDiagnosticSummaryMapper(new ObjectMapper());

    @Test
    void createsNullSafeBoundariesForKubernetesSummaries() {
        var summary = mapper.replicaSummary(null, 3, mapper.summary("phase", "Progressing"));

        assertThat(summary).containsEntry("availableReplicas", 0).containsEntry("desiredReplicas", 3);
        assertThat(mapper.statusFromSummary(summary)).isEqualTo("Progressing");
        assertThat(mapper.nullSafeMap(null)).isEmpty();
        assertThat(mapper.quantityMap(Map.of("cpu", new Quantity("250m")))).containsEntry("cpu", "250m");
        assertThat(mapper.writeJson(summary)).contains("\"desiredReplicas\":3");
    }
}
