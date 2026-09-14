package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ClusterSyncSettingJpaRepository extends JpaRepository<ClusterSyncSettingEntity, UUID> {

    Optional<ClusterSyncSettingEntity> findByClusterId(UUID clusterId);
}
