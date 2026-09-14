package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.analysis.AnalysisSession;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AnalysisSessionRepositoryPort {

    AnalysisSession save(AnalysisSession analysisSession);

    Optional<AnalysisSession> findById(UUID analysisId);

    List<AnalysisSession> findByIds(Set<UUID> analysisIds);

    Optional<AnalysisSession> findByAsyncJobId(UUID asyncJobId);

    Optional<AnalysisSession> findRunningByScope(UUID clusterId, UUID applicationId, String namespace);

    Optional<AnalysisSession> findLatestSucceededByScope(UUID clusterId, UUID applicationId, String namespace);

    List<AnalysisSession> findRecent(UUID clusterId, UUID applicationId, String namespace, int limit);

    void deleteById(UUID analysisId);
}
