package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.operations.OperationsReadinessModels.LiveValidationRun;
import io.strato.aiops.domain.operations.OperationsReadinessModels.OutcomeAggregate;
import io.strato.aiops.domain.operations.OperationsReadinessModels.ReliabilityTrendData;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OperationsReadinessRepositoryPort {

    /** OperationsReadinessRepositoryPort의 saveLiveValidationRun 처리에 필요한 데이터를 생성하거나 저장한다. */
    LiveValidationRun saveLiveValidationRun(LiveValidationRun run);

    /** OperationsReadinessRepositoryPort의 findLiveValidationRun 처리 결과를 조회해 반환한다. */
    Optional<LiveValidationRun> findLiveValidationRun(UUID runId);

    /** OperationsReadinessRepositoryPort의 findActiveLiveValidationRuns 처리 결과를 조회해 반환한다. */
    List<LiveValidationRun> findActiveLiveValidationRuns(UUID clusterId);

    /** OperationsReadinessRepositoryPort의 findExpiredLiveValidationRuns 처리 결과를 조회해 반환한다. */
    List<LiveValidationRun> findExpiredLiveValidationRuns(Instant now, int limit);

    /** OperationsReadinessRepositoryPort의 aggregateRemediationOutcomes 처리 계약을 정의한다. */
    List<OutcomeAggregate> aggregateRemediationOutcomes(String category, String resourceKind, int limit);

    /** OperationsReadinessRepositoryPort의 queryReliabilityTrend 처리 결과를 조회해 반환한다. */
    ReliabilityTrendData queryReliabilityTrend(UUID clusterId, String namespace, Instant from);
}
