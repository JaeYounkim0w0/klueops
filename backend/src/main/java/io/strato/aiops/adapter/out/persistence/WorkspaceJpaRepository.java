package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface WorkspaceJpaRepository extends JpaRepository<WorkspaceEntity, UUID> {
    Optional<WorkspaceEntity> findByTenantIdAndCode(UUID tenantId, String code);
    List<WorkspaceEntity> findByTenantIdOrderByNameAsc(UUID tenantId);
    List<WorkspaceEntity> findAllByOrderByNameAsc();
}
