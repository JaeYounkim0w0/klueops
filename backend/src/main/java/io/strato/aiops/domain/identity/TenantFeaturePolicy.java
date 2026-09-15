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

    public static TenantFeaturePolicy set(UUID tenantId, FeatureKey featureKey, boolean enabled,
                                          String actor, Instant now) {
        return new TenantFeaturePolicy(UUID.randomUUID(), tenantId, featureKey, enabled, actor, now);
    }
}
