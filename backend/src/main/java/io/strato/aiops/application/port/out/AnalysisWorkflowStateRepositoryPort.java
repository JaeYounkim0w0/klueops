package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.analysis.AnalysisWorkflowState;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisWorkflowStateRepositoryPort {

    /** AnalysisWorkflowStateRepositoryPort의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    AnalysisWorkflowState save(AnalysisWorkflowState workflowState);

    /** AnalysisWorkflowStateRepositoryPort의 findByAnalysisIdAndIssueGroupId 처리 결과를 조회해 반환한다. */
    Optional<AnalysisWorkflowState> findByAnalysisIdAndIssueGroupId(UUID analysisId, String issueGroupId);

    /** AnalysisWorkflowStateRepositoryPort의 findByAnalysisId 처리 결과를 조회해 반환한다. */
    List<AnalysisWorkflowState> findByAnalysisId(UUID analysisId);

    /** AnalysisWorkflowStateRepositoryPort의 deleteByAnalysisId 처리 대상과 관련 상태를 안전하게 정리한다. */
    void deleteByAnalysisId(UUID analysisId);
}
