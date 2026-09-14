package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.operations.OperationsModels.RegressionRun;
import io.strato.aiops.domain.operations.OperationsModels.WatchSignal;
import io.strato.aiops.domain.operations.OperationsModels.WatchSignalGroup;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisAssuranceRepositoryPort {

    void saveWatchSignal(WatchSignal signal);

    List<WatchSignal> findWatchSignals(UUID clusterId, String namespace, int limit);

    WatchSignalGroup saveWatchSignalGroup(WatchSignalGroup group);

    Optional<WatchSignalGroup> findWatchSignalGroup(UUID groupId);

    Optional<WatchSignalGroup> findWatchSignalGroupByFingerprint(String fingerprint);

    List<WatchSignalGroup> findWatchSignalGroups(UUID clusterId, String namespace, String state, int limit);

    RegressionRun saveRegressionRun(RegressionRun run);

    Optional<RegressionRun> findRegressionRun(UUID runId);

    List<RegressionRun> findRegressionRuns(int limit);
}
