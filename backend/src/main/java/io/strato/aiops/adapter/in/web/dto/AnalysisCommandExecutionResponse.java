package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.domain.analysis.AnalysisCommandExecution;
import io.strato.aiops.domain.analysis.AnalysisCommandSafety;
import io.strato.aiops.domain.analysis.AnalysisCommandStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "AI analysis command execution")
public record AnalysisCommandExecutionResponse(
        UUID id,
        UUID analysisId,
        UUID clusterId,
        String namespace,
        String command,
        AnalysisCommandSafety safety,
        AnalysisCommandStatus status,
        String reason,
        String stdoutText,
        String stderrText,
        Integer exitCode,
        Long durationMs,
        String createdBy,
        Instant createdAt
) {
    public static AnalysisCommandExecutionResponse from(AnalysisCommandExecution execution) {
        return new AnalysisCommandExecutionResponse(execution.id(), execution.analysisId(), execution.clusterId(),
                execution.namespace(), execution.command(), execution.safety(), execution.status(), execution.reason(),
                execution.stdoutText(), execution.stderrText(), execution.exitCode(), execution.durationMs(),
                execution.createdBy(), execution.createdAt());
    }
}
