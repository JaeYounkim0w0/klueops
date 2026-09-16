package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.identity.FeatureKey;
import io.strato.aiops.domain.identity.TenantFeaturePolicy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantFeaturePolicyRepositoryPort {
    /** TenantFeaturePolicyRepositoryPort의 findByTenantId 처리 결과를 조회해 반환한다. */
    List<TenantFeaturePolicy> findByTenantId(UUID tenantId);
    /** TenantFeaturePolicyRepositoryPort의 findByTenantIdAndFeatureKey 처리 결과를 조회해 반환한다. */
    Optional<TenantFeaturePolicy> findByTenantIdAndFeatureKey(UUID tenantId, FeatureKey featureKey);
    /** TenantFeaturePolicyRepositoryPort의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    TenantFeaturePolicy save(TenantFeaturePolicy policy);
}
