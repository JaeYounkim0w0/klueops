package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.OidcGroupMappingRepositoryPort;
import io.strato.aiops.domain.identity.OidcGroupMapping;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class OidcGroupMappingPersistenceAdapter implements OidcGroupMappingRepositoryPort {
    private final OidcGroupMappingJpaRepository repository;

    OidcGroupMappingPersistenceAdapter(OidcGroupMappingJpaRepository repository) {
        this.repository = repository;
    }

    @Override public List<OidcGroupMapping> findActive(String issuer, Collection<String> groups) {
        if (groups == null || groups.isEmpty()) return List.of();
        return repository.findByIssuerAndGroupValueInAndActiveTrue(issuer, groups).stream()
                .map(OidcGroupMappingEntity::toDomain).toList();
    }

    @Override public List<OidcGroupMapping> findByTenantId(UUID tenantId) {
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(OidcGroupMappingEntity::toDomain).toList();
    }

    @Override public Optional<OidcGroupMapping> findByIdAndTenantId(UUID id, UUID tenantId) {
        return repository.findByIdAndTenantId(id, tenantId).map(OidcGroupMappingEntity::toDomain);
    }

    @Override public OidcGroupMapping save(OidcGroupMapping mapping) {
        return repository.save(OidcGroupMappingEntity.fromDomain(mapping)).toDomain();
    }

    @Override public void delete(OidcGroupMapping mapping) {
        repository.delete(OidcGroupMappingEntity.fromDomain(mapping));
    }
}
