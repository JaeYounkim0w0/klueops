package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.analysis.AnalysisSession;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AnalysisSessionRepositoryPort {

    /** AnalysisSessionRepositoryPort의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    AnalysisSession save(AnalysisSession analysisSession);

    /** AnalysisSessionRepositoryPort의 findById 처리 결과를 조회해 반환한다. */
    Optional<AnalysisSession> findById(UUID analysisId);

    /** AnalysisSessionRepositoryPort의 findByIds 처리 결과를 조회해 반환한다. */
    List<AnalysisSession> findByIds(Set<UUID> analysisIds);

    /** AnalysisSessionRepositoryPort의 findByAsyncJobId 처리 결과를 조회해 반환한다. */
    Optional<AnalysisSession> findByAsyncJobId(UUID asyncJobId);

    /** AnalysisSessionRepositoryPort의 findRunningByScope 처리 결과를 조회해 반환한다. */
    Optional<AnalysisSession> findRunningByScope(UUID clusterId, UUID applicationId, String namespace);

    /** AnalysisSessionRepositoryPort의 findLatestSucceededByScope 처리 결과를 조회해 반환한다. */
    Optional<AnalysisSession> findLatestSucceededByScope(UUID clusterId, UUID applicationId, String namespace);

    /** AnalysisSessionRepositoryPort의 findRecent 처리 결과를 조회해 반환한다. */
    List<AnalysisSession> findRecent(UUID clusterId, UUID applicationId, String namespace, int limit);

    /** AnalysisSessionRepositoryPort의 deleteById 처리 대상과 관련 상태를 안전하게 정리한다. */
    void deleteById(UUID analysisId);
}
