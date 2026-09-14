package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.AnalysisWorkflowStateRepositoryPort;
import io.strato.aiops.domain.analysis.AnalysisWorkflowState;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaAnalysisWorkflowStateRepositoryAdapter implements AnalysisWorkflowStateRepositoryPort {

    private final AnalysisWorkflowStateJpaRepository repository;

    public JpaAnalysisWorkflowStateRepositoryAdapter(AnalysisWorkflowStateJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public AnalysisWorkflowState save(AnalysisWorkflowState workflowState) {
        return repository.save(AnalysisWorkflowStateEntity.fromDomain(workflowState)).toDomain();
    }

    @Override
    public Optional<AnalysisWorkflowState> findByAnalysisIdAndIssueGroupId(UUID analysisId, String issueGroupId) {
        return repository.findByAnalysisIdAndIssueGroupId(analysisId, issueGroupId)
                .map(AnalysisWorkflowStateEntity::toDomain);
    }

    @Override
    public List<AnalysisWorkflowState> findByAnalysisId(UUID analysisId) {
        return repository.findByAnalysisIdOrderByUpdatedAtDesc(analysisId).stream()
                .map(AnalysisWorkflowStateEntity::toDomain)
                .toList();
    }

    @Override
    public void deleteByAnalysisId(UUID analysisId) {
        repository.deleteByAnalysisId(analysisId);
    }
}
