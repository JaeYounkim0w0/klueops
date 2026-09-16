package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface RoleBindingJpaRepository extends JpaRepository<RoleBindingEntity, UUID> {
    /** RoleBindingJpaRepository의 findByPrincipalKeyIn 처리 결과를 조회해 반환한다. */
    List<RoleBindingEntity> findByPrincipalKeyIn(Collection<String> principalKeys);
}
