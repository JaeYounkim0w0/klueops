package io.strato.aiops.domain.applicationdelivery;

import io.strato.aiops.domain.cluster.EncryptedSecret;

import java.time.Instant;
import java.util.UUID;

public record ChartSource(UUID id, UUID tenantId, ChartSourceType sourceType, String name, String endpoint,
                          EncryptedSecret credential, String tlsPolicy, boolean enabled, String createdBy,
                          Instant createdAt, Instant updatedAt) {

    /** ChartSource의 create 처리에 필요한 데이터를 생성하거나 저장한다. */
    public static ChartSource create(UUID tenantId, ChartSourceType sourceType, String name, String endpoint,
                                     EncryptedSecret credential, String tlsPolicy, String actor, Instant now) {
        return new ChartSource(UUID.randomUUID(), tenantId, sourceType, name, endpoint, credential,
                tlsPolicy, true, actor, now, now);
    }

    /** ChartSource의 updated 처리 대상의 상태를 갱신한다. */
    public ChartSource updated(String name, String endpoint, EncryptedSecret credential, String tlsPolicy,
                               boolean enabled, Instant now) {
        return new ChartSource(id, tenantId, sourceType, name, endpoint,
                credential == null ? this.credential : credential, tlsPolicy, enabled, createdBy, createdAt, now);
    }
}
