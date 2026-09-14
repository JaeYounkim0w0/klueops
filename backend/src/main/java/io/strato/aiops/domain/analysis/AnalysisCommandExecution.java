package io.strato.aiops.domain.analysis;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AnalysisCommandExecution {

    private final UUID id;
    private final UUID analysisId;
    private final UUID clusterId;
    private final String namespace;
    private final String command;
    private final AnalysisCommandSafety safety;
    private final AnalysisCommandStatus status;
    private final String reason;
    private final String stdoutText;
    private final String stderrText;
    private final Integer exitCode;
    private final Long durationMs;
    private final String createdBy;
    private final Instant createdAt;

    public AnalysisCommandExecution(UUID id, UUID analysisId, UUID clusterId, String namespace, String command,
                                    AnalysisCommandSafety safety, AnalysisCommandStatus status, String reason,
                                    String stdoutText, String stderrText, Integer exitCode, Long durationMs,
                                    String createdBy, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.analysisId = Objects.requireNonNull(analysisId, "analysisId must not be null");
        this.clusterId = Objects.requireNonNull(clusterId, "clusterId must not be null");
        this.namespace = namespace;
        this.command = Objects.requireNonNull(command, "command must not be null");
        this.safety = Objects.requireNonNull(safety, "safety must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.reason = reason;
        this.stdoutText = stdoutText;
        this.stderrText = stderrText;
        this.exitCode = exitCode;
        this.durationMs = durationMs;
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static AnalysisCommandExecution succeeded(UUID analysisId, UUID clusterId, String namespace, String command,
                                                     AnalysisCommandSafety safety, String reason, String stdoutText,
                                                     long durationMs, String actor) {
        return new AnalysisCommandExecution(UUID.randomUUID(), analysisId, clusterId, namespace, command, safety,
                AnalysisCommandStatus.SUCCEEDED, reason, stdoutText, "", 0, durationMs, actor, Instant.now());
    }

    public static AnalysisCommandExecution failed(UUID analysisId, UUID clusterId, String namespace, String command,
                                                  AnalysisCommandSafety safety, String reason, String stderrText,
                                                  long durationMs, String actor) {
        return new AnalysisCommandExecution(UUID.randomUUID(), analysisId, clusterId, namespace, command, safety,
                AnalysisCommandStatus.FAILED, reason, "", stderrText, 1, durationMs, actor, Instant.now());
    }

    public static AnalysisCommandExecution blocked(UUID analysisId, UUID clusterId, String namespace, String command,
                                                   AnalysisCommandSafety safety, String reason, String actor) {
        return new AnalysisCommandExecution(UUID.randomUUID(), analysisId, clusterId, namespace, command, safety,
                AnalysisCommandStatus.BLOCKED, reason, "", reason, 126, 0L, actor, Instant.now());
    }

    public UUID id() { return id; }
    public UUID analysisId() { return analysisId; }
    public UUID clusterId() { return clusterId; }
    public String namespace() { return namespace; }
    public String command() { return command; }
    public AnalysisCommandSafety safety() { return safety; }
    public AnalysisCommandStatus status() { return status; }
    public String reason() { return reason; }
    public String stdoutText() { return stdoutText; }
    public String stderrText() { return stderrText; }
    public Integer exitCode() { return exitCode; }
    public Long durationMs() { return durationMs; }
    public String createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }
}
