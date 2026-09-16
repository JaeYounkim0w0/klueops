package io.strato.aiops.domain.applicationdelivery;

import java.time.Instant;
import java.util.UUID;

public record ValuesProfile(UUID id, UUID tenantId, UUID chartVersionId, String name, String description,
                            String createdBy, Instant createdAt, Instant updatedAt) {
    /** ValuesProfile의 create 처리에 필요한 데이터를 생성하거나 저장한다. */
    public static ValuesProfile create(UUID tenantId, UUID chartVersionId, String name, String description,
                                       String actor, Instant now) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        return new ValuesProfile(UUID.randomUUID(), tenantId, chartVersionId, name.trim(), description,
                actor, now, now);
    }
}
