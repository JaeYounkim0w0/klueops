package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.operations.OperationsModels.RegressionRun;
import io.strato.aiops.domain.operations.OperationsModels.WatchSignal;
import io.strato.aiops.domain.operations.OperationsModels.WatchSignalGroup;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisAssuranceRepositoryPort {

    /** AnalysisAssuranceRepositoryPort의 saveWatchSignal 처리에 필요한 데이터를 생성하거나 저장한다. */
    void saveWatchSignal(WatchSignal signal);

    /** AnalysisAssuranceRepositoryPort의 findWatchSignals 처리 결과를 조회해 반환한다. */
    List<WatchSignal> findWatchSignals(UUID clusterId, String namespace, int limit);

    /** AnalysisAssuranceRepositoryPort의 saveWatchSignalGroup 처리에 필요한 데이터를 생성하거나 저장한다. */
    WatchSignalGroup saveWatchSignalGroup(WatchSignalGroup group);

    /** AnalysisAssuranceRepositoryPort의 findWatchSignalGroup 처리 결과를 조회해 반환한다. */
    Optional<WatchSignalGroup> findWatchSignalGroup(UUID groupId);

    /** AnalysisAssuranceRepositoryPort의 findWatchSignalGroupByFingerprint 처리 결과를 조회해 반환한다. */
    Optional<WatchSignalGroup> findWatchSignalGroupByFingerprint(String fingerprint);

    /** AnalysisAssuranceRepositoryPort의 findWatchSignalGroups 처리 결과를 조회해 반환한다. */
    List<WatchSignalGroup> findWatchSignalGroups(UUID clusterId, String namespace, String state, int limit);

    /** AnalysisAssuranceRepositoryPort의 saveRegressionRun 처리에 필요한 데이터를 생성하거나 저장한다. */
    RegressionRun saveRegressionRun(RegressionRun run);

    /** AnalysisAssuranceRepositoryPort의 findRegressionRun 처리 결과를 조회해 반환한다. */
    Optional<RegressionRun> findRegressionRun(UUID runId);

    /** AnalysisAssuranceRepositoryPort의 findRegressionRuns 처리 결과를 조회해 반환한다. */
    List<RegressionRun> findRegressionRuns(int limit);
}
