package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.AnalysisSessionRepositoryPort;
import io.strato.aiops.domain.analysis.AnalysisSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class JpaAnalysisSessionRepositoryAdapter implements AnalysisSessionRepositoryPort {

    private final AnalysisSessionJpaRepository repository;

    /** JpaAnalysisSessionRepositoryAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public JpaAnalysisSessionRepositoryAdapter(AnalysisSessionJpaRepository repository) {
        this.repository = repository;
    }

    /** JpaAnalysisSessionRepositoryAdapter의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public AnalysisSession save(AnalysisSession analysisSession) {
        return repository.save(AnalysisSessionEntity.fromDomain(analysisSession)).toDomain();
    }

    /** JpaAnalysisSessionRepositoryAdapter의 findById 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<AnalysisSession> findById(UUID analysisId) {
        return repository.findById(analysisId).map(AnalysisSessionEntity::toDomain);
    }

    /** JpaAnalysisSessionRepositoryAdapter의 findByIds 처리 결과를 조회해 반환한다. */
    @Override
    public List<AnalysisSession> findByIds(Set<UUID> analysisIds) {
        if (analysisIds == null || analysisIds.isEmpty()) {
            return List.of();
        }
        return repository.findAllById(analysisIds).stream().map(AnalysisSessionEntity::toDomain).toList();
    }

    /** JpaAnalysisSessionRepositoryAdapter의 findByAsyncJobId 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<AnalysisSession> findByAsyncJobId(UUID asyncJobId) {
        return repository.findByAsyncJobId(asyncJobId).map(AnalysisSessionEntity::toDomain);
    }

    /** JpaAnalysisSessionRepositoryAdapter의 findRunningByScope 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<AnalysisSession> findRunningByScope(UUID clusterId, UUID applicationId, String namespace) {
        return repository.findRunningByScope(clusterId, applicationId, namespace, PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(AnalysisSessionEntity::toDomain);
    }

    /** JpaAnalysisSessionRepositoryAdapter의 findLatestSucceededByScope 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<AnalysisSession> findLatestSucceededByScope(UUID clusterId, UUID applicationId, String namespace) {
        return repository.findLatestSucceededByScope(clusterId, applicationId, namespace, PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(AnalysisSessionEntity::toDomain);
    }

    /** JpaAnalysisSessionRepositoryAdapter의 findRecent 처리 결과를 조회해 반환한다. */
    @Override
    public List<AnalysisSession> findRecent(UUID clusterId, UUID applicationId, String namespace, int limit) {
        return repository.findRecentByScope(clusterId, applicationId, namespace, PageRequest.of(0, limit)).stream()
                .map(AnalysisSessionEntity::toDomain)
                .toList();
    }

    /** JpaAnalysisSessionRepositoryAdapter의 deleteById 처리 대상과 관련 상태를 안전하게 정리한다. */
    @Override
    public void deleteById(UUID analysisId) {
        repository.deleteById(analysisId);
    }
}
