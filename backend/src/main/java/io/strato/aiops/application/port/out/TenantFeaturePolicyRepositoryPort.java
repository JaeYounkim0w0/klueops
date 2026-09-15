package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.identity.FeatureKey;
import io.strato.aiops.domain.identity.TenantFeaturePolicy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantFeaturePolicyRepositoryPort {
    List<TenantFeaturePolicy> findByTenantId(UUID tenantId);
    Optional<TenantFeaturePolicy> findByTenantIdAndFeatureKey(UUID tenantId, FeatureKey featureKey);
    TenantFeaturePolicy save(TenantFeaturePolicy policy);
}
