package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.analysis.AnalysisWorkflowState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisWorkflowStateRepositoryPort {

    AnalysisWorkflowState save(AnalysisWorkflowState workflowState);

    Optional<AnalysisWorkflowState> findByAnalysisIdAndIssueGroupId(UUID analysisId, String issueGroupId);

    List<AnalysisWorkflowState> findByAnalysisId(UUID analysisId);

    void deleteByAnalysisId(UUID analysisId);
}
