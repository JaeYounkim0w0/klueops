package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ClusterSyncSettingJpaRepository extends JpaRepository<ClusterSyncSettingEntity, UUID> {

    /** ClusterSyncSettingJpaRepository의 findByClusterId 처리 결과를 조회해 반환한다. */
    Optional<ClusterSyncSettingEntity> findByClusterId(UUID clusterId);
}
