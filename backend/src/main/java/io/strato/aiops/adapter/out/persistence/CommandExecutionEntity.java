package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.command.CommandExecution;
import io.strato.aiops.domain.command.CommandSafety;
import io.strato.aiops.domain.command.CommandStatus;
import io.strato.aiops.domain.command.CommandVerificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "command_executions")
class CommandExecutionEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID clusterId;
    private UUID sourceAnalysisId;
    private String namespace;
    @Column(name = "command_text", nullable = false, columnDefinition = "text")
    private String command;
    @Column(nullable = false, columnDefinition = "text")
    private String argvJson;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommandSafety safety;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommandStatus status;
    @Column(columnDefinition = "text")
    private String stdoutText;
    @Column(columnDefinition = "text")
    private String stderrText;
    private Integer exitCode;
    private Long durationMs;
    @Column(nullable = false)
    private boolean truncated;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommandVerificationStatus verificationStatus;
    @Column(columnDefinition = "text")
    private String verificationSummary;
    @Column(columnDefinition = "text")
    private String beforeSnapshot;
    @Column(columnDefinition = "text")
    private String afterSnapshot;
    @Column(columnDefinition = "text")
    private String rollbackCommand;
    private Instant verifiedAt;
    @Column(nullable = false)
    private String createdBy;
    private String requestId;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;

    protected CommandExecutionEntity() {
    }

    static CommandExecutionEntity fromDomain(CommandExecution value) {
        CommandExecutionEntity entity = new CommandExecutionEntity();
        entity.id = value.id();
        entity.clusterId = value.clusterId();
        entity.sourceAnalysisId = value.sourceAnalysisId();
        entity.namespace = value.namespace();
        entity.command = value.command();
        entity.argvJson = value.argvJson();
        entity.safety = value.safety();
        entity.status = value.status();
        entity.stdoutText = value.stdoutText();
        entity.stderrText = value.stderrText();
        entity.exitCode = value.exitCode();
        entity.durationMs = value.durationMs();
        entity.truncated = value.truncated();
        entity.verificationStatus = value.verificationStatus();
        entity.verificationSummary = value.verificationSummary();
        entity.beforeSnapshot = value.beforeSnapshot();
        entity.afterSnapshot = value.afterSnapshot();
        entity.rollbackCommand = value.rollbackCommand();
        entity.verifiedAt = value.verifiedAt();
        entity.createdBy = value.createdBy();
        entity.requestId = value.requestId();
        entity.createdAt = value.createdAt();
        entity.startedAt = value.startedAt();
        entity.completedAt = value.completedAt();
        return entity;
    }

    CommandExecution toDomain() {
        return new CommandExecution(id, clusterId, sourceAnalysisId, namespace, command, argvJson, safety, status, stdoutText,
                stderrText, exitCode, durationMs, truncated, verificationStatus, verificationSummary,
                beforeSnapshot, afterSnapshot, rollbackCommand, verifiedAt, createdBy, requestId, createdAt, startedAt, completedAt);
    }
}
