package io.strato.aiops.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI analysis workflow state update")
public record AnalysisWorkflowStateRequest(String status, String note) {
}
