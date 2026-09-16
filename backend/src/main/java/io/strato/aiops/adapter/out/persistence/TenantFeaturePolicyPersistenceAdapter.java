package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.TenantFeaturePolicyRepositoryPort;
import io.strato.aiops.domain.identity.FeatureKey;
import io.strato.aiops.domain.identity.TenantFeaturePolicy;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class TenantFeaturePolicyPersistenceAdapter implements TenantFeaturePolicyRepositoryPort {
    private final TenantFeaturePolicyJpaRepository repository;

    /** TenantFeaturePolicyPersistenceAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    TenantFeaturePolicyPersistenceAdapter(TenantFeaturePolicyJpaRepository repository) {
        this.repository = repository;
    }

    /** TenantFeaturePolicyPersistenceAdapter의 findByTenantId 처리 결과를 조회해 반환한다. */
    @Override public List<TenantFeaturePolicy> findByTenantId(UUID tenantId) {
        return repository.findByTenantIdOrderByFeatureKeyAsc(tenantId).stream().map(TenantFeaturePolicyEntity::toDomain).toList();
    }

    /** TenantFeaturePolicyPersistenceAdapter의 findByTenantIdAndFeatureKey 처리 결과를 조회해 반환한다. */
    @Override public Optional<TenantFeaturePolicy> findByTenantIdAndFeatureKey(UUID tenantId, FeatureKey featureKey) {
        return repository.findByTenantIdAndFeatureKey(tenantId, featureKey).map(TenantFeaturePolicyEntity::toDomain);
    }

    /** TenantFeaturePolicyPersistenceAdapter의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override public TenantFeaturePolicy save(TenantFeaturePolicy policy) {
        return repository.save(TenantFeaturePolicyEntity.fromDomain(policy)).toDomain();
    }
}
