package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.analysis.AnalysisCommandExecution;
import io.strato.aiops.domain.analysis.AnalysisCommandSafety;
import io.strato.aiops.domain.analysis.AnalysisCommandStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analysis_command_executions")
class AnalysisCommandExecutionEntity {

    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID analysisId;
    @Column(nullable = false)
    private UUID clusterId;
    private String namespace;
    @Column(name = "command_text", nullable = false, columnDefinition = "text")
    private String command;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisCommandSafety safety;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisCommandStatus status;
    @Column(length = 1000)
    private String reason;
    @Column(columnDefinition = "text")
    private String stdoutText;
    @Column(columnDefinition = "text")
    private String stderrText;
    private Integer exitCode;
    private Long durationMs;
    @Column(nullable = false)
    private String createdBy;
    @Column(nullable = false)
    private Instant createdAt;

    /** AnalysisCommandExecutionEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    protected AnalysisCommandExecutionEntity() {
    }

    /** AnalysisCommandExecutionEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    private AnalysisCommandExecutionEntity(UUID id, UUID analysisId, UUID clusterId, String namespace, String command,
                                           AnalysisCommandSafety safety, AnalysisCommandStatus status, String reason,
                                           String stdoutText, String stderrText, Integer exitCode, Long durationMs,
                                           String createdBy, Instant createdAt) {
        this.id = id;
        this.analysisId = analysisId;
        this.clusterId = clusterId;
        this.namespace = namespace;
        this.command = command;
        this.safety = safety;
        this.status = status;
        this.reason = reason;
        this.stdoutText = stdoutText;
        this.stderrText = stderrText;
        this.exitCode = exitCode;
        this.durationMs = durationMs;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    /** AnalysisCommandExecutionEntity의 fromDomain 처리 데이터를 필요한 표현으로 변환한다. */
    static AnalysisCommandExecutionEntity fromDomain(AnalysisCommandExecution execution) {
        return new AnalysisCommandExecutionEntity(execution.id(), execution.analysisId(), execution.clusterId(),
                execution.namespace(), execution.command(), execution.safety(), execution.status(), execution.reason(),
                execution.stdoutText(), execution.stderrText(), execution.exitCode(), execution.durationMs(),
                execution.createdBy(), execution.createdAt());
    }

    /** AnalysisCommandExecutionEntity의 toDomain 처리 데이터를 필요한 표현으로 변환한다. */
    AnalysisCommandExecution toDomain() {
        return new AnalysisCommandExecution(id, analysisId, clusterId, namespace, command, safety, status, reason,
                stdoutText, stderrText, exitCode, durationMs, createdBy, createdAt);
    }
}
