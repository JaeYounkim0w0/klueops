package io.strato.aiops.domain.tenancy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Tenant(
        UUID id,
        String code,
        String name,
        String description,
        TenantStatus status,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public Tenant {
        Objects.requireNonNull(id, "id is required");
        code = TenancyCode.normalize(code);
        name = requireText(name, "name");
        Objects.requireNonNull(status, "status is required");
        createdBy = requireText(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    public static Tenant create(String code, String name, String description, String actor) {
        Instant now = Instant.now();
        return new Tenant(UUID.randomUUID(), code, name, description, TenantStatus.ACTIVE, actor, now, now);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
