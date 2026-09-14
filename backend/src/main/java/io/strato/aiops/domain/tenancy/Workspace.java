package io.strato.aiops.domain.tenancy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Workspace(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String description,
        WorkspaceStatus status,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public Workspace {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        code = TenancyCode.normalize(code);
        name = requireText(name, "name");
        Objects.requireNonNull(status, "status is required");
        createdBy = requireText(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    public static Workspace create(UUID tenantId, String code, String name, String description, String actor) {
        Instant now = Instant.now();
        return new Workspace(UUID.randomUUID(), tenantId, code, name, description, WorkspaceStatus.ACTIVE, actor, now, now);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
