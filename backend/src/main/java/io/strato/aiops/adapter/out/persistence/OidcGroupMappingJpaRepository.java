package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface OidcGroupMappingJpaRepository extends JpaRepository<OidcGroupMappingEntity, UUID> {
    List<OidcGroupMappingEntity> findByIssuerAndGroupValueInAndActiveTrue(String issuer, Collection<String> groups);
    List<OidcGroupMappingEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<OidcGroupMappingEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
