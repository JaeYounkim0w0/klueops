package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.identity.TenantMembership;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantMembershipRepositoryPort {
    /** TenantMembershipRepositoryPort의 findByTenantId 처리 결과를 조회해 반환한다. */
    List<TenantMembership> findByTenantId(UUID tenantId);
    /** TenantMembershipRepositoryPort의 findByIdAndTenantId 처리 결과를 조회해 반환한다. */
    Optional<TenantMembership> findByIdAndTenantId(UUID id, UUID tenantId);
    /** TenantMembershipRepositoryPort의 findInvitedByIssuerAndSubject 처리 결과를 조회해 반환한다. */
    List<TenantMembership> findInvitedByIssuerAndSubject(String issuer, String subject);
    /** TenantMembershipRepositoryPort의 findInvitedByIssuerAndEmail 처리 결과를 조회해 반환한다. */
    List<TenantMembership> findInvitedByIssuerAndEmail(String issuer, String email);
    /** TenantMembershipRepositoryPort의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    TenantMembership save(TenantMembership membership);
}
