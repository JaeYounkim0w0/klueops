package io.strato.aiops.domain.applicationdelivery;

import io.strato.aiops.domain.cluster.EncryptedSecret;

import java.time.Instant;
import java.util.UUID;

public record ChartSource(UUID id, UUID tenantId, ChartSourceType sourceType, String name, String endpoint,
                          EncryptedSecret credential, String tlsPolicy, boolean enabled, String createdBy,
                          Instant createdAt, Instant updatedAt) {

    public static ChartSource create(UUID tenantId, ChartSourceType sourceType, String name, String endpoint,
                                     EncryptedSecret credential, String tlsPolicy, String actor, Instant now) {
        return new ChartSource(UUID.randomUUID(), tenantId, sourceType, name, endpoint, credential,
                tlsPolicy, true, actor, now, now);
    }

    public ChartSource updated(String name, String endpoint, EncryptedSecret credential, String tlsPolicy,
                               boolean enabled, Instant now) {
        return new ChartSource(id, tenantId, sourceType, name, endpoint,
                credential == null ? this.credential : credential, tlsPolicy, enabled, createdBy, createdAt, now);
    }
}
