package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.cluster.ClusterSyncSetting;

import java.util.UUID;

public interface GetClusterSyncSettingsUseCase {

    ClusterSyncSetting getClusterSyncSettings(UUID clusterId);
}
