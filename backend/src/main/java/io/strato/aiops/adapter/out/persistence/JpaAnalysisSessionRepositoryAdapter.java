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

    public JpaAnalysisSessionRepositoryAdapter(AnalysisSessionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public AnalysisSession save(AnalysisSession analysisSession) {
        return repository.save(AnalysisSessionEntity.fromDomain(analysisSession)).toDomain();
    }

    @Override
    public Optional<AnalysisSession> findById(UUID analysisId) {
        return repository.findById(analysisId).map(AnalysisSessionEntity::toDomain);
    }

    @Override
    public List<AnalysisSession> findByIds(Set<UUID> analysisIds) {
        if (analysisIds == null || analysisIds.isEmpty()) {
            return List.of();
        }
        return repository.findAllById(analysisIds).stream().map(AnalysisSessionEntity::toDomain).toList();
    }

    @Override
    public Optional<AnalysisSession> findByAsyncJobId(UUID asyncJobId) {
        return repository.findByAsyncJobId(asyncJobId).map(AnalysisSessionEntity::toDomain);
    }

    @Override
    public Optional<AnalysisSession> findRunningByScope(UUID clusterId, UUID applicationId, String namespace) {
        return repository.findRunningByScope(clusterId, applicationId, namespace, PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(AnalysisSessionEntity::toDomain);
    }

    @Override
    public Optional<AnalysisSession> findLatestSucceededByScope(UUID clusterId, UUID applicationId, String namespace) {
        return repository.findLatestSucceededByScope(clusterId, applicationId, namespace, PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(AnalysisSessionEntity::toDomain);
    }

    @Override
    public List<AnalysisSession> findRecent(UUID clusterId, UUID applicationId, String namespace, int limit) {
        return repository.findRecentByScope(clusterId, applicationId, namespace, PageRequest.of(0, limit)).stream()
                .map(AnalysisSessionEntity::toDomain)
                .toList();
    }

    @Override
    public void deleteById(UUID analysisId) {
        repository.deleteById(analysisId);
    }
}
