package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.AnalysisCommandExecutionRepositoryPort;
import io.strato.aiops.domain.analysis.AnalysisCommandExecution;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class JpaAnalysisCommandExecutionRepositoryAdapter implements AnalysisCommandExecutionRepositoryPort {

    private final AnalysisCommandExecutionJpaRepository repository;

    public JpaAnalysisCommandExecutionRepositoryAdapter(AnalysisCommandExecutionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public AnalysisCommandExecution save(AnalysisCommandExecution execution) {
        return repository.save(AnalysisCommandExecutionEntity.fromDomain(execution)).toDomain();
    }

    @Override
    public List<AnalysisCommandExecution> findByAnalysisId(UUID analysisId) {
        return repository.findByAnalysisIdOrderByCreatedAtDesc(analysisId).stream()
                .map(AnalysisCommandExecutionEntity::toDomain)
                .toList();
    }

    @Override
    public void deleteByAnalysisId(UUID analysisId) {
        repository.deleteByAnalysisId(analysisId);
    }
}
