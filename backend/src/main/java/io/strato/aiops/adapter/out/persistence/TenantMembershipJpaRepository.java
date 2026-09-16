package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import io.strato.aiops.domain.identity.MembershipStatus;

interface TenantMembershipJpaRepository extends JpaRepository<TenantMembershipEntity, UUID> {
    /** TenantMembershipJpaRepository의 findByTenantIdOrderByCreatedAtDesc 처리 결과를 조회해 반환한다. */
    List<TenantMembershipEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    /** TenantMembershipJpaRepository의 findByIdAndTenantId 처리 결과를 조회해 반환한다. */
    Optional<TenantMembershipEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    /** TenantMembershipJpaRepository의 findByPendingIssuerAndPendingSubjectAndStatus 처리 결과를 조회해 반환한다. */
    List<TenantMembershipEntity> findByPendingIssuerAndPendingSubjectAndStatus(
            String issuer, String subject, MembershipStatus status);
    /** TenantMembershipJpaRepository의 findByPendingIssuerAndPendingEmailIgnoreCaseAndStatus 처리 결과를 조회해 반환한다. */
    List<TenantMembershipEntity> findByPendingIssuerAndPendingEmailIgnoreCaseAndStatus(
            String issuer, String email, MembershipStatus status);
}
