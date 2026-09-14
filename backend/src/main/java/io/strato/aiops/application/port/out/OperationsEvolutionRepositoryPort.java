package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.operations.OperationsEvolutionModels.AiReleaseGate;
import io.strato.aiops.domain.operations.OperationsEvolutionModels.IncidentPostmortem;
import io.strato.aiops.domain.operations.OperationsEvolutionModels.RemediationObservation;
import io.strato.aiops.domain.operations.OperationsEvolutionModels.SignalNoisePolicy;
import io.strato.aiops.domain.operations.OperationsEvolutionModels.WatchContinuity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OperationsEvolutionRepositoryPort {

    WatchContinuity saveWatchContinuity(WatchContinuity continuity);

    Optional<WatchContinuity> findWatchContinuity(UUID clusterId);

    List<WatchContinuity> findWatchContinuities();

    SignalNoisePolicy saveNoisePolicy(SignalNoisePolicy policy);

    Optional<SignalNoisePolicy> findNoisePolicy(UUID policyId);

    List<SignalNoisePolicy> findNoisePolicies();

    void deleteNoisePolicy(UUID policyId);

    default Optional<SignalNoisePolicy> findApplicableNoisePolicy(UUID clusterId, String namespace, Instant now) {
        return findNoisePolicies().stream()
                .filter(policy -> policy.applies(clusterId, namespace, now))
                .findFirst();
    }

    RemediationObservation saveRemediationObservation(RemediationObservation observation);

    Optional<RemediationObservation> findRemediationObservation(UUID observationId);

    List<RemediationObservation> findRemediationObservations(UUID incidentId, String state, int limit);

    AiReleaseGate saveReleaseGate(AiReleaseGate gate);

    List<AiReleaseGate> findReleaseGates(int limit);

    IncidentPostmortem savePostmortem(IncidentPostmortem postmortem);

    Optional<IncidentPostmortem> findPostmortem(UUID incidentId);
}
