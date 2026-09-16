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

    /** AnalysisCommandExecution 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
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

    /** AnalysisCommandExecution의 succeeded 처리에 필요한 업무 로직을 수행한다. */
    public static AnalysisCommandExecution succeeded(UUID analysisId, UUID clusterId, String namespace, String command,
                                                     AnalysisCommandSafety safety, String reason, String stdoutText,
                                                     long durationMs, String actor) {
        return new AnalysisCommandExecution(UUID.randomUUID(), analysisId, clusterId, namespace, command, safety,
                AnalysisCommandStatus.SUCCEEDED, reason, stdoutText, "", 0, durationMs, actor, Instant.now());
    }

    /** AnalysisCommandExecution의 failed 처리에 필요한 업무 로직을 수행한다. */
    public static AnalysisCommandExecution failed(UUID analysisId, UUID clusterId, String namespace, String command,
                                                  AnalysisCommandSafety safety, String reason, String stderrText,
                                                  long durationMs, String actor) {
        return new AnalysisCommandExecution(UUID.randomUUID(), analysisId, clusterId, namespace, command, safety,
                AnalysisCommandStatus.FAILED, reason, "", stderrText, 1, durationMs, actor, Instant.now());
    }

    /** AnalysisCommandExecution의 blocked 처리에 필요한 업무 로직을 수행한다. */
    public static AnalysisCommandExecution blocked(UUID analysisId, UUID clusterId, String namespace, String command,
                                                   AnalysisCommandSafety safety, String reason, String actor) {
        return new AnalysisCommandExecution(UUID.randomUUID(), analysisId, clusterId, namespace, command, safety,
                AnalysisCommandStatus.BLOCKED, reason, "", reason, 126, 0L, actor, Instant.now());
    }

    /** AnalysisCommandExecution의 id 처리에 필요한 업무 로직을 수행한다. */
    public UUID id() { return id; }
    /** AnalysisCommandExecution의 analysisId 처리에 필요한 업무 로직을 수행한다. */
    public UUID analysisId() { return analysisId; }
    /** AnalysisCommandExecution의 clusterId 처리에 필요한 업무 로직을 수행한다. */
    public UUID clusterId() { return clusterId; }
    /** AnalysisCommandExecution의 namespace 처리에 필요한 업무 로직을 수행한다. */
    public String namespace() { return namespace; }
    /** AnalysisCommandExecution의 command 처리에 필요한 업무 로직을 수행한다. */
    public String command() { return command; }
    /** AnalysisCommandExecution의 safety 처리에 필요한 업무 로직을 수행한다. */
    public AnalysisCommandSafety safety() { return safety; }
    /** AnalysisCommandExecution의 status 처리에 필요한 업무 로직을 수행한다. */
    public AnalysisCommandStatus status() { return status; }
    /** AnalysisCommandExecution의 reason 처리에 필요한 업무 로직을 수행한다. */
    public String reason() { return reason; }
    /** AnalysisCommandExecution의 stdoutText 처리에 필요한 업무 로직을 수행한다. */
    public String stdoutText() { return stdoutText; }
    /** AnalysisCommandExecution의 stderrText 처리에 필요한 업무 로직을 수행한다. */
    public String stderrText() { return stderrText; }
    /** AnalysisCommandExecution의 exitCode 처리에 필요한 업무 로직을 수행한다. */
    public Integer exitCode() { return exitCode; }
    /** AnalysisCommandExecution의 durationMs 처리에 필요한 업무 로직을 수행한다. */
    public Long durationMs() { return durationMs; }
    /** AnalysisCommandExecution의 createdBy 처리에 필요한 데이터를 생성하거나 저장한다. */
    public String createdBy() { return createdBy; }
    /** AnalysisCommandExecution의 createdAt 처리에 필요한 데이터를 생성하거나 저장한다. */
    public Instant createdAt() { return createdAt; }
}
