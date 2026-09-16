package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.domain.analysis.AnalysisWorkflowState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "AI analysis workflow state")
public record AnalysisWorkflowStateResponse(
        UUID id,
        UUID analysisId,
        String issueGroupId,
        String status,
        String note,
        String updatedBy,
        Instant updatedAt
) {
    /** AnalysisWorkflowStateResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
    public static AnalysisWorkflowStateResponse from(AnalysisWorkflowState workflowState) {
        return new AnalysisWorkflowStateResponse(workflowState.id(), workflowState.analysisId(),
                workflowState.issueGroupId(), workflowState.status(), workflowState.note(),
                workflowState.updatedBy(), workflowState.updatedAt());
    }
}
