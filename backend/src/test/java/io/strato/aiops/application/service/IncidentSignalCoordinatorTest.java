package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.domain.analysis.AnalysisSession;
import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.domain.cluster.ClusterEnvironment;
import io.strato.aiops.domain.cluster.ClusterProvider;
import io.strato.aiops.domain.sync.KubernetesEventSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentSignalCoordinatorTest {

    /** IncidentSignalCoordinatorTest의 convertsHighSignalWarningEventToFactualIncidentSignal 처리 데이터를 필요한 표현으로 변환한다. */
    @Test
    void convertsHighSignalWarningEventToFactualIncidentSignal() {
        KubernetesEventSnapshot event = event("FailedMount", "Warning", 1);

        IncidentSignalCoordinator.IncidentSignal signal = IncidentSignalCoordinator.eventSignal(event);

        assertThat(signal).isNotNull();
        assertThat(signal.category()).isEqualTo("STORAGE_CONFIG");
        assertThat(signal.severity()).isEqualTo("HIGH");
        assertThat(signal.evidenceType()).isEqualTo("KUBERNETES_EVENT");
        assertThat(signal.factual()).isTrue();
    }

    /** IncidentSignalCoordinatorTest의 ignoresNormalAndLowFrequencyNonHighSignalEvents 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void ignoresNormalAndLowFrequencyNonHighSignalEvents() {
        assertThat(IncidentSignalCoordinator.eventSignal(event("Scheduled", "Normal", 20))).isNull();
        assertThat(IncidentSignalCoordinator.eventSignal(event("CustomWarning", "Warning", 2))).isNull();
    }

    /** IncidentSignalCoordinatorTest의 convertsOnlyHighRiskAiFindingsAndExtractsResourceReference 처리 데이터를 필요한 표현으로 변환한다. */
    @Test
    void convertsOnlyHighRiskAiFindingsAndExtractsResourceReference() {
        Cluster cluster = Cluster.register("dev", "test", ClusterEnvironment.DEV,
                ClusterProvider.KIND, "local", "test");
        AnalysisSession analysis = AnalysisSession.succeeded(cluster.id(), null, "nginx", """
                {"severity":"MEDIUM","issueGroups":[
                  {"severity":"HIGH","category":"ROLLOUT","title":"Deployment/api rollout stalled",
                   "summary":"available replicas are below desired","recommendation":"inspect rollout"},
                  {"severity":"LOW","title":"Pod/cache minor warning"}
                ]}
                """, "test");

        List<IncidentSignalCoordinator.IncidentSignal> signals = IncidentSignalCoordinator.analysisSignals(
                cluster, analysis, new ObjectMapper());

        assertThat(signals).singleElement().satisfies(signal -> {
            assertThat(signal.resourceKind()).isEqualTo("Deployment");
            assertThat(signal.resourceName()).isEqualTo("api");
            assertThat(signal.category()).isEqualTo("ROLLOUT");
            assertThat(signal.evidenceType()).isEqualTo("AI_INFERENCE");
            assertThat(signal.factual()).isFalse();
        });
    }

    /** IncidentSignalCoordinatorTest의 event 처리에 필요한 업무 로직을 수행한다. */
    private KubernetesEventSnapshot event(String reason, String type, int count) {
        Instant now = Instant.parse("2026-09-07T00:00:00Z");
        return new KubernetesEventSnapshot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "default",
                "Pod", "api", reason, type, "event message", now, count, now);
    }
}
