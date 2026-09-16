package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface UserAccountJpaRepository extends JpaRepository<UserAccountEntity, UUID> {
    /** UserAccountJpaRepository의 findByIssuerAndSubject 처리 결과를 조회해 반환한다. */
    Optional<UserAccountEntity> findByIssuerAndSubject(String issuer, String subject);
}
