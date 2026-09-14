package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.StartAnalysisJobResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "AI analysis async job start response")
public record StartAnalysisJobResponse(
        @Schema(description = "Started async job ID") UUID jobId,
        @Schema(description = "Created analysis session ID") UUID analysisId
) {
    public static StartAnalysisJobResponse from(StartAnalysisJobResult result) {
        return new StartAnalysisJobResponse(result.jobId(), result.analysisId());
    }
}
