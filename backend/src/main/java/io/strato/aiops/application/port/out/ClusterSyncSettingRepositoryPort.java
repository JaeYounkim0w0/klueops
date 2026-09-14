package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.cluster.ClusterSyncSetting;

import java.util.Optional;
import java.util.UUID;

public interface ClusterSyncSettingRepositoryPort {

    ClusterSyncSetting save(ClusterSyncSetting syncSetting);

    Optional<ClusterSyncSetting> findByClusterId(UUID clusterId);
}
