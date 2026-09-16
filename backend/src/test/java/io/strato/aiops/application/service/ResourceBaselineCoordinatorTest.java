package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.domain.cluster.ClusterEnvironment;
import io.strato.aiops.domain.cluster.ClusterProvider;
import io.strato.aiops.domain.operations.OperationsModels.ResourceBaseline;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceBaselineCoordinatorTest {

    private final ResourceBaselineCoordinator coordinator =
            new ResourceBaselineCoordinator(null, new ObjectMapper());
    private final Instant detectedAt = Instant.parse("2026-09-07T01:00:00Z");

    /** ResourceBaselineCoordinatorTest의 createsInitialBaselineWithoutChangeNoise 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Test
    void createsInitialBaselineWithoutChangeNoise() {
        Cluster cluster = cluster();

        ResourceBaselineCoordinator.BaselinePlan plan = coordinator.plan(cluster,
                List.of(snapshot(cluster, "Running", "{\"ready\":1}")), List.of(), detectedAt);

        assertThat(plan.baselines()).hasSize(1);
        assertThat(plan.changes()).isEmpty();
        assertThat(plan.deletedBaselineIds()).isEmpty();
    }

    /** ResourceBaselineCoordinatorTest의 classifiesStatusOnlyDifferenceAsStatusChanged 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void classifiesStatusOnlyDifferenceAsStatusChanged() {
        Cluster cluster = cluster();
        ResourceBaseline previous = coordinator.plan(cluster,
                List.of(snapshot(cluster, "Pending", "{\"ready\":1}")), List.of(), detectedAt)
                .baselines().get(0);

        ResourceBaselineCoordinator.BaselinePlan plan = coordinator.plan(cluster,
                List.of(snapshot(cluster, "Running", "{\"ready\":1}")), List.of(previous), detectedAt);

        assertThat(plan.changes()).singleElement().satisfies(change -> {
            assertThat(change.changeType()).isEqualTo("STATUS_CHANGED");
            assertThat(change.previousStatus()).isEqualTo("Pending");
            assertThat(change.currentStatus()).isEqualTo("Running");
        });
    }

    /** ResourceBaselineCoordinatorTest의 recordsMissingResourceAsDeletedForBoundedInventory 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void recordsMissingResourceAsDeletedForBoundedInventory() {
        Cluster cluster = cluster();
        ResourceBaseline kept = coordinator.plan(cluster,
                List.of(snapshot(cluster, "Running", "{}")), List.of(), detectedAt).baselines().get(0);
        ResourceBaseline missing = new ResourceBaseline(UUID.randomUUID(), cluster.id(), "default", "Service", "api",
                "Active", "old-hash", "{}", detectedAt.minusSeconds(60));

        ResourceBaselineCoordinator.BaselinePlan plan = coordinator.plan(cluster,
                List.of(snapshot(cluster, "Running", "{}")), List.of(kept, missing), detectedAt);

        assertThat(plan.deletedBaselineIds()).containsExactly(missing.id());
        assertThat(plan.changes()).extracting(change -> change.changeType()).contains("DELETED");
    }

    /** ResourceBaselineCoordinatorTest의 cluster 처리에 필요한 업무 로직을 수행한다. */
    private Cluster cluster() {
        return Cluster.register("baseline", "test", ClusterEnvironment.DEV, ClusterProvider.KIND, "local", "test");
    }

    /** ResourceBaselineCoordinatorTest의 snapshot 처리에 필요한 업무 로직을 수행한다. */
    private KubernetesResourceSnapshot snapshot(Cluster cluster, String status, String summary) {
        return new KubernetesResourceSnapshot(UUID.randomUUID(), cluster.id(), UUID.randomUUID(), "default", "Pod",
                "api", UUID.randomUUID().toString(), status, summary, null, false, detectedAt.minusSeconds(10));
    }
}
