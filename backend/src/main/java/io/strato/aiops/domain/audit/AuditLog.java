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

    public AuditLog(UUID id, String action, String targetType, String targetId, String actor, String requestId, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.targetType = Objects.requireNonNull(targetType, "targetType must not be null");
        this.targetId = Objects.requireNonNull(targetId, "targetId must not be null");
        this.actor = Objects.requireNonNull(actor, "actor must not be null");
        this.requestId = requestId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static AuditLog create(String action, String targetType, String targetId, String actor, String requestId) {
        return new AuditLog(UUID.randomUUID(), action, targetType, targetId, actor, requestId, Instant.now());
    }

    public UUID id() {
        return id;
    }

    public String action() {
        return action;
    }

    public String targetType() {
        return targetType;
    }

    public String targetId() {
        return targetId;
    }

    public String actor() {
        return actor;
    }

    public String requestId() {
        return requestId;
    }

    public Instant createdAt() {
        return createdAt;
    }
}

