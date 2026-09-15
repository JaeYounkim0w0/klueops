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

    TenantFeaturePolicyPersistenceAdapter(TenantFeaturePolicyJpaRepository repository) {
        this.repository = repository;
    }

    @Override public List<TenantFeaturePolicy> findByTenantId(UUID tenantId) {
        return repository.findByTenantIdOrderByFeatureKeyAsc(tenantId).stream().map(TenantFeaturePolicyEntity::toDomain).toList();
    }

    @Override public Optional<TenantFeaturePolicy> findByTenantIdAndFeatureKey(UUID tenantId, FeatureKey featureKey) {
        return repository.findByTenantIdAndFeatureKey(tenantId, featureKey).map(TenantFeaturePolicyEntity::toDomain);
    }

    @Override public TenantFeaturePolicy save(TenantFeaturePolicy policy) {
        return repository.save(TenantFeaturePolicyEntity.fromDomain(policy)).toDomain();
    }
}
