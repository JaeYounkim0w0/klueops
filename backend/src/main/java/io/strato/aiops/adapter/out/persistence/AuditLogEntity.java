package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.audit.AuditLog;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
class AuditLogEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String targetType;

    @Column(nullable = false)
    private String targetId;

    @Column(nullable = false)
    private String actor;

    private String requestId;

    @Column(nullable = false)
    private Instant createdAt;

    /** AuditLogEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    protected AuditLogEntity() {
    }

    /** AuditLogEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    private AuditLogEntity(UUID id, String action, String targetType, String targetId, String actor, String requestId, Instant createdAt) {
        this.id = id;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.actor = actor;
        this.requestId = requestId;
        this.createdAt = createdAt;
    }

    /** AuditLogEntity의 fromDomain 처리 데이터를 필요한 표현으로 변환한다. */
    static AuditLogEntity fromDomain(AuditLog auditLog) {
        return new AuditLogEntity(
                auditLog.id(),
                auditLog.action(),
                auditLog.targetType(),
                auditLog.targetId(),
                auditLog.actor(),
                auditLog.requestId(),
                auditLog.createdAt()
        );
    }

    /** AuditLogEntity의 toDomain 처리 데이터를 필요한 표현으로 변환한다. */
    AuditLog toDomain() {
        return new AuditLog(id, action, targetType, targetId, actor, requestId, createdAt);
    }
}

