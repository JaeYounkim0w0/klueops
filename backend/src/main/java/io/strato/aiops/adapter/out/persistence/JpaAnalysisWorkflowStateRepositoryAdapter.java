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

    /** JpaAnalysisWorkflowStateRepositoryAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public JpaAnalysisWorkflowStateRepositoryAdapter(AnalysisWorkflowStateJpaRepository repository) {
        this.repository = repository;
    }

    /** JpaAnalysisWorkflowStateRepositoryAdapter의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public AnalysisWorkflowState save(AnalysisWorkflowState workflowState) {
        return repository.save(AnalysisWorkflowStateEntity.fromDomain(workflowState)).toDomain();
    }

    /** JpaAnalysisWorkflowStateRepositoryAdapter의 findByAnalysisIdAndIssueGroupId 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<AnalysisWorkflowState> findByAnalysisIdAndIssueGroupId(UUID analysisId, String issueGroupId) {
        return repository.findByAnalysisIdAndIssueGroupId(analysisId, issueGroupId)
                .map(AnalysisWorkflowStateEntity::toDomain);
    }

    /** JpaAnalysisWorkflowStateRepositoryAdapter의 findByAnalysisId 처리 결과를 조회해 반환한다. */
    @Override
    public List<AnalysisWorkflowState> findByAnalysisId(UUID analysisId) {
        return repository.findByAnalysisIdOrderByUpdatedAtDesc(analysisId).stream()
                .map(AnalysisWorkflowStateEntity::toDomain)
                .toList();
    }

    /** JpaAnalysisWorkflowStateRepositoryAdapter의 deleteByAnalysisId 처리 대상과 관련 상태를 안전하게 정리한다. */
    @Override
    public void deleteByAnalysisId(UUID analysisId) {
        repository.deleteByAnalysisId(analysisId);
    }
}
