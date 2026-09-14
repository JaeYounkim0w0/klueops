package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AnalysisWorkflowStateJpaRepository extends JpaRepository<AnalysisWorkflowStateEntity, UUID> {

    Optional<AnalysisWorkflowStateEntity> findByAnalysisIdAndIssueGroupId(UUID analysisId, String issueGroupId);

    List<AnalysisWorkflowStateEntity> findByAnalysisIdOrderByUpdatedAtDesc(UUID analysisId);

    void deleteByAnalysisId(UUID analysisId);
}
