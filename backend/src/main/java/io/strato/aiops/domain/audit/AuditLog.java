package io.strato.aiops.domain.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AuditLog {

    private final UUID id;
    private final String action;
    private final String targetType;
    private final String targetId;
    private final String actor;
    private final String requestId;
    private final Instant createdAt;

    /** AuditLog 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AuditLog(UUID id, String action, String targetType, String targetId, String actor, String requestId, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.targetType = Objects.requireNonNull(targetType, "targetType must not be null");
        this.targetId = Objects.requireNonNull(targetId, "targetId must not be null");
        this.actor = Objects.requireNonNull(actor, "actor must not be null");
        this.requestId = requestId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /** AuditLog의 create 처리에 필요한 데이터를 생성하거나 저장한다. */
    public static AuditLog create(String action, String targetType, String targetId, String actor, String requestId) {
        return new AuditLog(UUID.randomUUID(), action, targetType, targetId, actor, requestId, Instant.now());
    }

    /** AuditLog의 id 처리에 필요한 업무 로직을 수행한다. */
    public UUID id() {
        return id;
    }

    /** AuditLog의 action 처리에 필요한 업무 로직을 수행한다. */
    public String action() {
        return action;
    }

    /** AuditLog의 targetType 처리에 필요한 업무 로직을 수행한다. */
    public String targetType() {
        return targetType;
    }

    /** AuditLog의 targetId 처리에 필요한 업무 로직을 수행한다. */
    public String targetId() {
        return targetId;
    }

    /** AuditLog의 actor 처리에 필요한 업무 로직을 수행한다. */
    public String actor() {
        return actor;
    }

    /** AuditLog의 requestId 처리에 필요한 업무 로직을 수행한다. */
    public String requestId() {
        return requestId;
    }

    /** AuditLog의 createdAt 처리에 필요한 데이터를 생성하거나 저장한다. */
    public Instant createdAt() {
        return createdAt;
    }
}

