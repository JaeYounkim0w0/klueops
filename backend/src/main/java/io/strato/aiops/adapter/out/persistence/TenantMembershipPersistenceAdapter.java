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

    /** TenantMembershipPersistenceAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    TenantMembershipPersistenceAdapter(TenantMembershipJpaRepository repository) {
        this.repository = repository;
    }

    /** TenantMembershipPersistenceAdapter의 findByTenantId 처리 결과를 조회해 반환한다. */
    @Override public List<TenantMembership> findByTenantId(UUID tenantId) {
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(TenantMembershipEntity::toDomain).toList();
    }

    /** TenantMembershipPersistenceAdapter의 findByIdAndTenantId 처리 결과를 조회해 반환한다. */
    @Override public Optional<TenantMembership> findByIdAndTenantId(UUID id, UUID tenantId) {
        return repository.findByIdAndTenantId(id, tenantId).map(TenantMembershipEntity::toDomain);
    }

    /** TenantMembershipPersistenceAdapter의 findInvitedByIssuerAndSubject 처리 결과를 조회해 반환한다. */
    @Override public List<TenantMembership> findInvitedByIssuerAndSubject(String issuer, String subject) {
        return repository.findByPendingIssuerAndPendingSubjectAndStatus(issuer, subject, MembershipStatus.INVITED)
                .stream().map(TenantMembershipEntity::toDomain).toList();
    }

    /** TenantMembershipPersistenceAdapter의 findInvitedByIssuerAndEmail 처리 결과를 조회해 반환한다. */
    @Override public List<TenantMembership> findInvitedByIssuerAndEmail(String issuer, String email) {
        return repository.findByPendingIssuerAndPendingEmailIgnoreCaseAndStatus(issuer, email, MembershipStatus.INVITED)
                .stream().map(TenantMembershipEntity::toDomain).toList();
    }

    /** TenantMembershipPersistenceAdapter의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override public TenantMembership save(TenantMembership membership) {
        return repository.save(TenantMembershipEntity.fromDomain(membership)).toDomain();
    }
}
