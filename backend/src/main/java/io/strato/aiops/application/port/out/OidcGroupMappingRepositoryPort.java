package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.identity.OidcGroupMapping;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OidcGroupMappingRepositoryPort {
    List<OidcGroupMapping> findActive(String issuer, Collection<String> groups);
    List<OidcGroupMapping> findByTenantId(UUID tenantId);
    Optional<OidcGroupMapping> findByIdAndTenantId(UUID id, UUID tenantId);
    OidcGroupMapping save(OidcGroupMapping mapping);
    void delete(OidcGroupMapping mapping);
}
