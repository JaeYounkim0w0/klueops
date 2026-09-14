package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.ClusterSyncSettingRepositoryPort;
import io.strato.aiops.domain.cluster.ClusterSyncSetting;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaClusterSyncSettingRepositoryAdapter implements ClusterSyncSettingRepositoryPort {

    private final ClusterSyncSettingJpaRepository clusterSyncSettingJpaRepository;

    public JpaClusterSyncSettingRepositoryAdapter(ClusterSyncSettingJpaRepository clusterSyncSettingJpaRepository) {
        this.clusterSyncSettingJpaRepository = clusterSyncSettingJpaRepository;
    }

    @Override
    public ClusterSyncSetting save(ClusterSyncSetting syncSetting) {
        return clusterSyncSettingJpaRepository.save(ClusterSyncSettingEntity.fromDomain(syncSetting)).toDomain();
    }

    @Override
    public Optional<ClusterSyncSetting> findByClusterId(UUID clusterId) {
        return clusterSyncSettingJpaRepository.findByClusterId(clusterId)
                .map(ClusterSyncSettingEntity::toDomain);
    }
}
