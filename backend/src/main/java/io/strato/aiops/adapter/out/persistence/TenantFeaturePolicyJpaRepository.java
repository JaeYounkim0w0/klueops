package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.identity.FeatureKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TenantFeaturePolicyJpaRepository extends JpaRepository<TenantFeaturePolicyEntity, UUID> {
    /** TenantFeaturePolicyJpaRepository의 findByTenantIdOrderByFeatureKeyAsc 처리 결과를 조회해 반환한다. */
    List<TenantFeaturePolicyEntity> findByTenantIdOrderByFeatureKeyAsc(UUID tenantId);
    /** TenantFeaturePolicyJpaRepository의 findByTenantIdAndFeatureKey 처리 결과를 조회해 반환한다. */
    Optional<TenantFeaturePolicyEntity> findByTenantIdAndFeatureKey(UUID tenantId, FeatureKey featureKey);
}
