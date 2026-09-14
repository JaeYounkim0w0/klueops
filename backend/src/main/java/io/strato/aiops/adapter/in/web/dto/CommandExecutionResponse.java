package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.domain.command.CommandExecution;

import java.time.Instant;
import java.util.UUID;

public record CommandExecutionResponse(
        UUID id,
        UUID clusterId,
        UUID sourceAnalysisId,
        String namespace,
        String command,
        String safety,
        String status,
        String stdoutText,
        String stderrText,
        Integer exitCode,
        Long durationMs,
        boolean truncated,
        String verificationStatus,
        String verificationSummary,
        String beforeSnapshot,
        String afterSnapshot,
        String rollbackCommand,
        Instant verifiedAt,
        String createdBy,
        String requestId,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
) {
    public static CommandExecutionResponse from(CommandExecution value) {
        return new CommandExecutionResponse(value.id(), value.clusterId(), value.sourceAnalysisId(), value.namespace(), value.command(),
                value.safety().name(), value.status().name(), value.stdoutText(), value.stderrText(), value.exitCode(),
                value.durationMs(), value.truncated(), value.verificationStatus().name(), value.verificationSummary(),
                value.beforeSnapshot(), value.afterSnapshot(), value.rollbackCommand(), value.verifiedAt(),
                value.createdBy(), value.requestId(), value.createdAt(),
                value.startedAt(), value.completedAt());
    }
}
