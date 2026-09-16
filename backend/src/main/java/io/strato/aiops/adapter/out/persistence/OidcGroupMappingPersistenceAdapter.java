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

    /** OidcGroupMappingPersistenceAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    OidcGroupMappingPersistenceAdapter(OidcGroupMappingJpaRepository repository) {
        this.repository = repository;
    }

    /** OidcGroupMappingPersistenceAdapter의 findActive 처리 결과를 조회해 반환한다. */
    @Override public List<OidcGroupMapping> findActive(String issuer, Collection<String> groups) {
        if (groups == null || groups.isEmpty()) return List.of();
        return repository.findByIssuerAndGroupValueInAndActiveTrue(issuer, groups).stream()
                .map(OidcGroupMappingEntity::toDomain).toList();
    }

    /** OidcGroupMappingPersistenceAdapter의 findByTenantId 처리 결과를 조회해 반환한다. */
    @Override public List<OidcGroupMapping> findByTenantId(UUID tenantId) {
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(OidcGroupMappingEntity::toDomain).toList();
    }

    /** OidcGroupMappingPersistenceAdapter의 findByIdAndTenantId 처리 결과를 조회해 반환한다. */
    @Override public Optional<OidcGroupMapping> findByIdAndTenantId(UUID id, UUID tenantId) {
        return repository.findByIdAndTenantId(id, tenantId).map(OidcGroupMappingEntity::toDomain);
    }

    /** OidcGroupMappingPersistenceAdapter의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override public OidcGroupMapping save(OidcGroupMapping mapping) {
        return repository.save(OidcGroupMappingEntity.fromDomain(mapping)).toDomain();
    }

    /** OidcGroupMappingPersistenceAdapter의 delete 처리 대상과 관련 상태를 안전하게 정리한다. */
    @Override public void delete(OidcGroupMapping mapping) {
        repository.delete(OidcGroupMappingEntity.fromDomain(mapping));
    }
}
