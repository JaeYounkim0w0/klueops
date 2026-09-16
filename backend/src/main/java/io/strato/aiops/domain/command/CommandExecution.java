package io.strato.aiops.domain.command;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CommandExecution(
        UUID id,
        UUID clusterId,
        UUID sourceAnalysisId,
        String namespace,
        String command,
        String argvJson,
        CommandSafety safety,
        CommandStatus status,
        String stdoutText,
        String stderrText,
        Integer exitCode,
        Long durationMs,
        boolean truncated,
        CommandVerificationStatus verificationStatus,
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
    /** CommandExecution 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public CommandExecution {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(clusterId, "clusterId must not be null");
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(argvJson, "argvJson must not be null");
        Objects.requireNonNull(safety, "safety must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(verificationStatus, "verificationStatus must not be null");
        Objects.requireNonNull(createdBy, "createdBy must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /** CommandExecution의 queued 처리에 필요한 업무 로직을 수행한다. */
    public static CommandExecution queued(UUID clusterId, UUID sourceAnalysisId, String namespace, String command, String argvJson,
                                          CommandSafety safety, String actor, String requestId, Instant now) {
        return new CommandExecution(UUID.randomUUID(), clusterId, sourceAnalysisId, namespace, command, argvJson, safety,
                CommandStatus.QUEUED, "", "", null, null, false,
                safety == CommandSafety.CHANGE || safety == CommandSafety.DESTRUCTIVE
                        ? CommandVerificationStatus.PENDING : CommandVerificationStatus.NOT_REQUIRED,
                "", "", "", null, null, actor, requestId, now, null, null);
    }

    /** CommandExecution의 running 처리의 핵심 작업 흐름을 실행한다. */
    public CommandExecution running(Instant now) {
        return new CommandExecution(id, clusterId, sourceAnalysisId, namespace, command, argvJson, safety, CommandStatus.RUNNING,
                stdoutText, stderrText, exitCode, durationMs, truncated, verificationStatus, verificationSummary,
                beforeSnapshot, afterSnapshot, rollbackCommand, verifiedAt, createdBy, requestId, createdAt, now, null);
    }

    /** CommandExecution의 completed 처리에 필요한 업무 로직을 수행한다. */
    public CommandExecution completed(CommandStatus finalStatus, String stdout, String stderr, Integer code,
                                      long duration, boolean outputTruncated, Instant now) {
        return new CommandExecution(id, clusterId, sourceAnalysisId, namespace, command, argvJson, safety, finalStatus,
                stdout, stderr, code, duration, outputTruncated, verificationStatus, verificationSummary,
                beforeSnapshot, afterSnapshot, rollbackCommand, verifiedAt, createdBy, requestId, createdAt,
                startedAt == null ? createdAt : startedAt, now);
    }

    /** CommandExecution의 verified 처리에 필요한 업무 로직을 수행한다. */
    public CommandExecution verified(CommandVerificationStatus verification, String summary, String before,
                                     String after, String rollback, Instant now) {
        return new CommandExecution(id, clusterId, sourceAnalysisId, namespace, command, argvJson, safety, status,
                stdoutText, stderrText, exitCode, durationMs, truncated, verification, summary,
                before, after, rollback, now, createdBy, requestId, createdAt, startedAt, completedAt);
    }
}
