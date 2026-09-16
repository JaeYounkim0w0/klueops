package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface WorkspaceJpaRepository extends JpaRepository<WorkspaceEntity, UUID> {
    /** WorkspaceJpaRepository의 findByTenantIdAndCode 처리 결과를 조회해 반환한다. */
    Optional<WorkspaceEntity> findByTenantIdAndCode(UUID tenantId, String code);
    /** WorkspaceJpaRepository의 findByTenantIdOrderByNameAsc 처리 결과를 조회해 반환한다. */
    List<WorkspaceEntity> findByTenantIdOrderByNameAsc(UUID tenantId);
    /** WorkspaceJpaRepository의 findAllByOrderByNameAsc 처리 결과를 조회해 반환한다. */
    List<WorkspaceEntity> findAllByOrderByNameAsc();
}
