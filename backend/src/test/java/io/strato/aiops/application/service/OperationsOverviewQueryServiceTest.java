package io.strato.aiops.application.service;

import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.domain.cluster.ClusterEnvironment;
import io.strato.aiops.domain.cluster.ClusterProvider;
import io.strato.aiops.domain.operations.OperationsModels.CapacityPosture;
import io.strato.aiops.domain.operations.OperationsModels.ClusterHealth;
import io.strato.aiops.domain.operations.OperationsModels.Incident;
import io.strato.aiops.domain.operations.OperationsModels.IncidentState;
import io.strato.aiops.domain.operations.OperationsModels.PolicyEvaluation;
import io.strato.aiops.domain.operations.OperationsModels.PolicyResult;
import io.strato.aiops.domain.sync.KubernetesEventSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OperationsOverviewQueryServiceTest {

    /** OperationsOverviewQueryServiceTest의 calculatesClusterHealthFromOpenIncidentsPoliciesAndWarningEvents 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void calculatesClusterHealthFromOpenIncidentsPoliciesAndWarningEvents() {
        Cluster cluster = cluster();
        Incident high = incident(cluster, "HIGH");
        PolicyEvaluation failed = evaluation(cluster, "WORKLOAD_AVAILABILITY", PolicyResult.FAIL);
        KubernetesEventSnapshot warning = event(cluster, "Warning");

        ClusterHealth health = OperationsOverviewQueryService.clusterHealth(
                cluster, List.of(high), List.of(failed), List.of(warning));

        assertThat(health.healthScore()).isEqualTo(76);
        assertThat(health.openIncidents()).isEqualTo(1);
        assertThat(health.failedPolicies()).isEqualTo(1);
        assertThat(health.warningEvents()).isEqualTo(1);
        assertThat(health.posture()).isEqualTo("ATTENTION");
    }

    /** OperationsOverviewQueryServiceTest의 summarizesConfigurationBasedCapacitySignals 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void summarizesConfigurationBasedCapacitySignals() {
        Cluster cluster = cluster();
        List<PolicyEvaluation> evaluations = List.of(
                evaluation(cluster, "WORKLOAD_AVAILABILITY", PolicyResult.FAIL),
                evaluation(cluster, "POD_UNHEALTHY", PolicyResult.FAIL),
                evaluation(cluster, "PVC_PENDING", PolicyResult.FAIL),
                evaluation(cluster, "NETWORK_POLICY_MISSING", PolicyResult.WARN),
                evaluation(cluster, "RESOURCE_GOVERNANCE_UNKNOWN", PolicyResult.NOT_APPLICABLE)
        );

        CapacityPosture posture = OperationsOverviewQueryService.capacityPosture(evaluations);

        assertThat(posture.dataSource()).isEqualTo("CONFIGURATION_BASED");
        assertThat(posture.unavailableWorkloads()).isEqualTo(1);
        assertThat(posture.unhealthyPods()).isEqualTo(1);
        assertThat(posture.pendingPvcs()).isEqualTo(1);
        assertThat(posture.namespacesWithoutNetworkPolicy()).isEqualTo(1);
        assertThat(posture.governanceEvidenceGaps()).isEqualTo(1);
    }

    /** OperationsOverviewQueryServiceTest의 cluster 처리에 필요한 업무 로직을 수행한다. */
    private Cluster cluster() {
        return Cluster.register("overview", "test", ClusterEnvironment.DEV, ClusterProvider.KIND, "local", "test");
    }

    /** OperationsOverviewQueryServiceTest의 incident 처리에 필요한 업무 로직을 수행한다. */
    private Incident incident(Cluster cluster, String severity) {
        Instant now = Instant.parse("2026-09-07T00:00:00Z");
        return new Incident(UUID.randomUUID(), "fingerprint", cluster.id(), cluster.name(), "default", "Pod", "api",
                "PROBE", severity, IncidentState.OPEN, "Pod unhealthy", "summary", "inspect", 1, 0, null,
                now.minusSeconds(60), now, "test");
    }

    /** OperationsOverviewQueryServiceTest의 evaluation 처리에 필요한 업무 로직을 수행한다. */
    private PolicyEvaluation evaluation(Cluster cluster, String policyId, PolicyResult result) {
        return new PolicyEvaluation(UUID.randomUUID(), policyId, cluster.id(), cluster.name(), "default", "Pod", "api",
                result, "evidence", "recommendation", Instant.parse("2026-09-07T00:00:00Z"));
    }

    /** OperationsOverviewQueryServiceTest의 event 처리에 필요한 업무 로직을 수행한다. */
    private KubernetesEventSnapshot event(Cluster cluster, String type) {
        Instant now = Instant.parse("2026-09-07T00:00:30Z");
        return new KubernetesEventSnapshot(UUID.randomUUID(), cluster.id(), UUID.randomUUID(), "default", "Pod", "api",
                "Unhealthy", type, "probe failed", now, 1, now);
    }
}
