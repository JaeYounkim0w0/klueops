package io.strato.aiops.domain.applicationdelivery;

import java.time.Instant;
import java.util.UUID;

public record ValuesProfile(UUID id, UUID tenantId, UUID chartVersionId, String name, String description,
                            String createdBy, Instant createdAt, Instant updatedAt) {
    public static ValuesProfile create(UUID tenantId, UUID chartVersionId, String name, String description,
                                       String actor, Instant now) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        return new ValuesProfile(UUID.randomUUID(), tenantId, chartVersionId, name.trim(), description,
                actor, now, now);
    }
}
