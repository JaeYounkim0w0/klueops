package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.cluster.ClusterSyncSetting;

import java.util.UUID;

public interface UpdateClusterSyncSettingsUseCase {

    /** UpdateClusterSyncSettingsUseCase의 updateClusterSyncSettings 처리 대상의 상태를 갱신한다. */
    ClusterSyncSetting updateClusterSyncSettings(UUID clusterId, UpdateClusterSyncSettingsCommand command, String actor, String requestId);
}
