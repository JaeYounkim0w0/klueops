package io.strato.aiops.domain.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TenantFeaturePolicy(
        UUID id,
        UUID tenantId,
        FeatureKey featureKey,
        boolean enabled,
        String updatedBy,
        Instant updatedAt
) {
    /** TenantFeaturePolicy 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public TenantFeaturePolicy {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(featureKey, "featureKey is required");
        Objects.requireNonNull(updatedBy, "updatedBy is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (featureKey.mandatory() && !enabled) {
            throw new IllegalArgumentException("Mandatory tenant features cannot be disabled");
        }
    }

    /** TenantFeaturePolicy의 set 처리 대상의 상태를 갱신한다. */
    public static TenantFeaturePolicy set(UUID tenantId, FeatureKey featureKey, boolean enabled,
                                          String actor, Instant now) {
        return new TenantFeaturePolicy(UUID.randomUUID(), tenantId, featureKey, enabled, actor, now);
    }
}
