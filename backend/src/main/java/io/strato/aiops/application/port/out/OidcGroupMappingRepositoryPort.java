package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.identity.OidcGroupMapping;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OidcGroupMappingRepositoryPort {
    /** OidcGroupMappingRepositoryPort의 findActive 처리 결과를 조회해 반환한다. */
    List<OidcGroupMapping> findActive(String issuer, Collection<String> groups);
    /** OidcGroupMappingRepositoryPort의 findByTenantId 처리 결과를 조회해 반환한다. */
    List<OidcGroupMapping> findByTenantId(UUID tenantId);
    /** OidcGroupMappingRepositoryPort의 findByIdAndTenantId 처리 결과를 조회해 반환한다. */
    Optional<OidcGroupMapping> findByIdAndTenantId(UUID id, UUID tenantId);
    /** OidcGroupMappingRepositoryPort의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    OidcGroupMapping save(OidcGroupMapping mapping);
    /** OidcGroupMappingRepositoryPort의 delete 처리 대상과 관련 상태를 안전하게 정리한다. */
    void delete(OidcGroupMapping mapping);
}
