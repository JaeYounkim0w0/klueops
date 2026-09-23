package io.product.aiops.application.port.in;

import io.product.aiops.domain.cluster.ClusterSyncSetting;

import java.util.UUID;

public interface GetClusterSyncSettingsUseCase {

    /** GetClusterSyncSettingsUseCase의 getClusterSyncSettings 처리 결과를 조회해 반환한다. */
    ClusterSyncSetting getClusterSyncSettings(UUID clusterId);
}
