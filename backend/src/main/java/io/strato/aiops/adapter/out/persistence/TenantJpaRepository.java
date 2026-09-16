package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TenantJpaRepository extends JpaRepository<TenantEntity, UUID> {
    /** TenantJpaRepository의 findByCode 처리 결과를 조회해 반환한다. */
    Optional<TenantEntity> findByCode(String code);
    /** TenantJpaRepository의 findAllByOrderByNameAsc 처리 결과를 조회해 반환한다. */
    List<TenantEntity> findAllByOrderByNameAsc();
}
