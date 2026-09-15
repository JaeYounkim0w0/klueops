package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.TenantMembershipRepositoryPort;
import io.strato.aiops.domain.identity.TenantMembership;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import io.strato.aiops.domain.identity.MembershipStatus;

@Repository
class TenantMembershipPersistenceAdapter implements TenantMembershipRepositoryPort {
    private final TenantMembershipJpaRepository repository;

    TenantMembershipPersistenceAdapter(TenantMembershipJpaRepository repository) {
        this.repository = repository;
    }

    @Override public List<TenantMembership> findByTenantId(UUID tenantId) {
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(TenantMembershipEntity::toDomain).toList();
    }

    @Override public Optional<TenantMembership> findByIdAndTenantId(UUID id, UUID tenantId) {
        return repository.findByIdAndTenantId(id, tenantId).map(TenantMembershipEntity::toDomain);
    }

    @Override public List<TenantMembership> findInvitedByIssuerAndSubject(String issuer, String subject) {
        return repository.findByPendingIssuerAndPendingSubjectAndStatus(issuer, subject, MembershipStatus.INVITED)
                .stream().map(TenantMembershipEntity::toDomain).toList();
    }

    @Override public List<TenantMembership> findInvitedByIssuerAndEmail(String issuer, String email) {
        return repository.findByPendingIssuerAndPendingEmailIgnoreCaseAndStatus(issuer, email, MembershipStatus.INVITED)
                .stream().map(TenantMembershipEntity::toDomain).toList();
    }

    @Override public TenantMembership save(TenantMembership membership) {
        return repository.save(TenantMembershipEntity.fromDomain(membership)).toDomain();
    }
}
