package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface RoleBindingJpaRepository extends JpaRepository<RoleBindingEntity, UUID> {
    List<RoleBindingEntity> findByPrincipalKeyIn(Collection<String> principalKeys);
}
