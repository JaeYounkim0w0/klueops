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

    /** OperationsEvolutionRepositoryPort의 saveWatchContinuity 처리에 필요한 데이터를 생성하거나 저장한다. */
    WatchContinuity saveWatchContinuity(WatchContinuity continuity);

    /** OperationsEvolutionRepositoryPort의 findWatchContinuity 처리 결과를 조회해 반환한다. */
    Optional<WatchContinuity> findWatchContinuity(UUID clusterId);

    /** OperationsEvolutionRepositoryPort의 findWatchContinuities 처리 결과를 조회해 반환한다. */
    List<WatchContinuity> findWatchContinuities();

    /** OperationsEvolutionRepositoryPort의 saveNoisePolicy 처리에 필요한 데이터를 생성하거나 저장한다. */
    SignalNoisePolicy saveNoisePolicy(SignalNoisePolicy policy);

    /** OperationsEvolutionRepositoryPort의 findNoisePolicy 처리 결과를 조회해 반환한다. */
    Optional<SignalNoisePolicy> findNoisePolicy(UUID policyId);

    /** OperationsEvolutionRepositoryPort의 findNoisePolicies 처리 결과를 조회해 반환한다. */
    List<SignalNoisePolicy> findNoisePolicies();

    /** OperationsEvolutionRepositoryPort의 deleteNoisePolicy 처리 대상과 관련 상태를 안전하게 정리한다. */
    void deleteNoisePolicy(UUID policyId);

    /** OperationsEvolutionRepositoryPort의 findApplicableNoisePolicy 처리 결과를 조회해 반환한다. */
    default Optional<SignalNoisePolicy> findApplicableNoisePolicy(UUID clusterId, String namespace, Instant now) {
        return findNoisePolicies().stream()
                .filter(policy -> policy.applies(clusterId, namespace, now))
                .findFirst();
    }

    /** OperationsEvolutionRepositoryPort의 saveRemediationObservation 처리에 필요한 데이터를 생성하거나 저장한다. */
    RemediationObservation saveRemediationObservation(RemediationObservation observation);

    /** OperationsEvolutionRepositoryPort의 findRemediationObservation 처리 결과를 조회해 반환한다. */
    Optional<RemediationObservation> findRemediationObservation(UUID observationId);

    /** OperationsEvolutionRepositoryPort의 findRemediationObservations 처리 결과를 조회해 반환한다. */
    List<RemediationObservation> findRemediationObservations(UUID incidentId, String state, int limit);

    /** OperationsEvolutionRepositoryPort의 saveReleaseGate 처리에 필요한 데이터를 생성하거나 저장한다. */
    AiReleaseGate saveReleaseGate(AiReleaseGate gate);

    /** OperationsEvolutionRepositoryPort의 findReleaseGates 처리 결과를 조회해 반환한다. */
    List<AiReleaseGate> findReleaseGates(int limit);

    /** OperationsEvolutionRepositoryPort의 savePostmortem 처리에 필요한 데이터를 생성하거나 저장한다. */
    IncidentPostmortem savePostmortem(IncidentPostmortem postmortem);

    /** OperationsEvolutionRepositoryPort의 findPostmortem 처리 결과를 조회해 반환한다. */
    Optional<IncidentPostmortem> findPostmortem(UUID incidentId);
}
