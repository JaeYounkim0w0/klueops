package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AnalysisWorkflowStateJpaRepository extends JpaRepository<AnalysisWorkflowStateEntity, UUID> {

    /** AnalysisWorkflowStateJpaRepository의 findByAnalysisIdAndIssueGroupId 처리 결과를 조회해 반환한다. */
    Optional<AnalysisWorkflowStateEntity> findByAnalysisIdAndIssueGroupId(UUID analysisId, String issueGroupId);

    /** AnalysisWorkflowStateJpaRepository의 findByAnalysisIdOrderByUpdatedAtDesc 처리 결과를 조회해 반환한다. */
    List<AnalysisWorkflowStateEntity> findByAnalysisIdOrderByUpdatedAtDesc(UUID analysisId);

    /** AnalysisWorkflowStateJpaRepository의 deleteByAnalysisId 처리 대상과 관련 상태를 안전하게 정리한다. */
    void deleteByAnalysisId(UUID analysisId);
}
