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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cluster from ClusterEntity cluster where cluster.id = :clusterId")
    Optional<ClusterEntity> findByIdForUpdate(@Param("clusterId") UUID clusterId);

    List<ClusterEntity> findAllByOrderByCreatedAtDesc();

    List<ClusterEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<ClusterEntity> findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(UUID tenantId, UUID workspaceId);
}
