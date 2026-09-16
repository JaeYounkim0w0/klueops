package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ClusterJpaRepository extends JpaRepository<ClusterEntity, UUID> {

    /** ClusterJpaRepository의 findByIdForUpdate 처리 결과를 조회해 반환한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cluster from ClusterEntity cluster where cluster.id = :clusterId")
    Optional<ClusterEntity> findByIdForUpdate(@Param("clusterId") UUID clusterId);

    /** ClusterJpaRepository의 findAllByOrderByCreatedAtDesc 처리 결과를 조회해 반환한다. */
    List<ClusterEntity> findAllByOrderByCreatedAtDesc();

    /** ClusterJpaRepository의 findByTenantIdOrderByCreatedAtDesc 처리 결과를 조회해 반환한다. */
    List<ClusterEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    /** ClusterJpaRepository의 findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc 처리 결과를 조회해 반환한다. */
    List<ClusterEntity> findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(UUID tenantId, UUID workspaceId);
}
