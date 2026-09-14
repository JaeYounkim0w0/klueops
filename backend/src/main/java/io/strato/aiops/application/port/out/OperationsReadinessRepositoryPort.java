package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.operations.OperationsReadinessModels.LiveValidationRun;
import io.strato.aiops.domain.operations.OperationsReadinessModels.OutcomeAggregate;
import io.strato.aiops.domain.operations.OperationsReadinessModels.ReliabilityTrendData;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OperationsReadinessRepositoryPort {

    LiveValidationRun saveLiveValidationRun(LiveValidationRun run);

    Optional<LiveValidationRun> findLiveValidationRun(UUID runId);

    List<LiveValidationRun> findActiveLiveValidationRuns(UUID clusterId);

    List<LiveValidationRun> findExpiredLiveValidationRuns(Instant now, int limit);

    List<OutcomeAggregate> aggregateRemediationOutcomes(String category, String resourceKind, int limit);

    ReliabilityTrendData queryReliabilityTrend(UUID clusterId, String namespace, Instant from);
}
