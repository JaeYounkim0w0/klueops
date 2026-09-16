package io.strato.aiops.application.service;

import io.strato.aiops.domain.operations.OperationsModels.Incident;
import io.strato.aiops.domain.operations.OperationsModels.IncidentRecovery;
import io.strato.aiops.domain.operations.OperationsModels.IncidentState;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentRecoveryCoordinatorTest {

    /** IncidentRecoveryCoordinatorTest의 movesOpenIncidentToMonitoringAfterFirstHealthyObservation 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void movesOpenIncidentToMonitoringAfterFirstHealthyObservation() {
        Incident incident = incident(IncidentState.OPEN, 0);
        KubernetesResourceSnapshot resource = podSnapshot("Running", incident.lastDetectedAt().plusSeconds(30));

        IncidentRecoveryCoordinator.RecoveryDecision decision = IncidentRecoveryCoordinator.decide(
                incident, resource, null);

        assertThat(decision.healthy()).isTrue();
        assertThat(decision.consecutiveHealthyCount()).isEqualTo(1);
        assertThat(decision.nextState()).isEqualTo(IncidentState.MONITORING);
        assertThat(decision.activityType()).isEqualTo("AUTO_MONITORING");
        assertThat(decision.notificationType()).isNull();
    }

    /** IncidentRecoveryCoordinatorTest의 resolvesIncidentAfterSecondDistinctHealthyObservation 처리에 필요한 결과를 조합해 반환한다. */
    @Test
    void resolvesIncidentAfterSecondDistinctHealthyObservation() {
        Incident incident = incident(IncidentState.MONITORING, 0);
        Instant observedAt = incident.lastDetectedAt().plusSeconds(60);
        IncidentRecovery previous = new IncidentRecovery(incident.id(), 1, 2,
                incident.lastDetectedAt().plusSeconds(30), incident.lastDetectedAt().plusSeconds(30), "Running", true);

        IncidentRecoveryCoordinator.RecoveryDecision decision = IncidentRecoveryCoordinator.decide(
                incident, podSnapshot("Running", observedAt), previous);

        assertThat(decision.consecutiveHealthyCount()).isEqualTo(2);
        assertThat(decision.nextState()).isEqualTo(IncidentState.RESOLVED);
        assertThat(decision.activityType()).isEqualTo("AUTO_RESOLVED");
        assertThat(decision.notificationType()).isEqualTo("INCIDENT_AUTO_RESOLVED");
    }

    /** IncidentRecoveryCoordinatorTest의 reopensMonitoringIncidentWhenNewSnapshotIsUnhealthy 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void reopensMonitoringIncidentWhenNewSnapshotIsUnhealthy() {
        Incident incident = incident(IncidentState.MONITORING, 1);

        IncidentRecoveryCoordinator.RecoveryDecision decision = IncidentRecoveryCoordinator.decide(
                incident, podSnapshot("Pending", incident.lastDetectedAt().plusSeconds(30)), null);

        assertThat(decision.healthy()).isFalse();
        assertThat(decision.consecutiveHealthyCount()).isZero();
        assertThat(decision.nextState()).isEqualTo(IncidentState.REOPENED);
        assertThat(decision.reopenCount()).isEqualTo(2);
        assertThat(decision.notificationType()).isEqualTo("INCIDENT_REOPENED");
    }

    /** IncidentRecoveryCoordinatorTest의 incident 처리에 필요한 업무 로직을 수행한다. */
    private Incident incident(IncidentState state, int reopenCount) {
        Instant detectedAt = Instant.parse("2026-09-07T00:00:00Z");
        return new Incident(UUID.randomUUID(), "fingerprint", UUID.randomUUID(), "cluster", "default",
                "Pod", "api", "STORAGE", "HIGH", state, "Pod unavailable", "summary", "next",
                1, reopenCount, null, detectedAt.minusSeconds(60), detectedAt, "operator");
    }

    /** IncidentRecoveryCoordinatorTest의 podSnapshot 처리에 필요한 업무 로직을 수행한다. */
    private KubernetesResourceSnapshot podSnapshot(String status, Instant collectedAt) {
        return new KubernetesResourceSnapshot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "default",
                "Pod", "api", UUID.randomUUID().toString(), status, "{}", null, false, collectedAt);
    }
}
