package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import io.strato.aiops.domain.identity.MembershipStatus;

interface TenantMembershipJpaRepository extends JpaRepository<TenantMembershipEntity, UUID> {
    List<TenantMembershipEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<TenantMembershipEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    List<TenantMembershipEntity> findByPendingIssuerAndPendingSubjectAndStatus(
            String issuer, String subject, MembershipStatus status);
    List<TenantMembershipEntity> findByPendingIssuerAndPendingEmailIgnoreCaseAndStatus(
            String issuer, String email, MembershipStatus status);
}
