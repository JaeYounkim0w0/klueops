package io.strato.aiops.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI analysis command request")
public record AnalysisCommandRequest(String command, String confirmText) {
}
