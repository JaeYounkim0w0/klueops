package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.cluster.ClusterSyncSetting;

import java.util.UUID;

public interface UpdateClusterSyncSettingsUseCase {

    ClusterSyncSetting updateClusterSyncSettings(UUID clusterId, UpdateClusterSyncSettingsCommand command, String actor, String requestId);
}
