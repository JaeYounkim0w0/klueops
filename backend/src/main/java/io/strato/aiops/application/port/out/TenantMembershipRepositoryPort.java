package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.identity.TenantMembership;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantMembershipRepositoryPort {
    List<TenantMembership> findByTenantId(UUID tenantId);
    Optional<TenantMembership> findByIdAndTenantId(UUID id, UUID tenantId);
    List<TenantMembership> findInvitedByIssuerAndSubject(String issuer, String subject);
    List<TenantMembership> findInvitedByIssuerAndEmail(String issuer, String email);
    TenantMembership save(TenantMembership membership);
}
