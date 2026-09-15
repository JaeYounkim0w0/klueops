package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.identity.FeatureKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TenantFeaturePolicyJpaRepository extends JpaRepository<TenantFeaturePolicyEntity, UUID> {
    List<TenantFeaturePolicyEntity> findByTenantIdOrderByFeatureKeyAsc(UUID tenantId);
    Optional<TenantFeaturePolicyEntity> findByTenantIdAndFeatureKey(UUID tenantId, FeatureKey featureKey);
}
