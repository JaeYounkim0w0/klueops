package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface OidcGroupMappingJpaRepository extends JpaRepository<OidcGroupMappingEntity, UUID> {
    /** OidcGroupMappingJpaRepository의 findByIssuerAndGroupValueInAndActiveTrue 처리 결과를 조회해 반환한다. */
    List<OidcGroupMappingEntity> findByIssuerAndGroupValueInAndActiveTrue(String issuer, Collection<String> groups);
    /** OidcGroupMappingJpaRepository의 findByTenantIdOrderByCreatedAtDesc 처리 결과를 조회해 반환한다. */
    List<OidcGroupMappingEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    /** OidcGroupMappingJpaRepository의 findByIdAndTenantId 처리 결과를 조회해 반환한다. */
    Optional<OidcGroupMappingEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
