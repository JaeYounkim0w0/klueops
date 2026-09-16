package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.cluster.ClusterSyncSetting;

import java.util.UUID;

public interface GetClusterSyncSettingsUseCase {

    /** GetClusterSyncSettingsUseCase의 getClusterSyncSettings 처리 결과를 조회해 반환한다. */
    ClusterSyncSetting getClusterSyncSettings(UUID clusterId);
}
