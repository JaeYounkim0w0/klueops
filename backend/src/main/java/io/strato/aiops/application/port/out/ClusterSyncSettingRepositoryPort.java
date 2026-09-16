package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.cluster.ClusterSyncSetting;

import java.util.Optional;
import java.util.UUID;

public interface ClusterSyncSettingRepositoryPort {

    /** ClusterSyncSettingRepositoryPort의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    ClusterSyncSetting save(ClusterSyncSetting syncSetting);

    /** ClusterSyncSettingRepositoryPort의 findByClusterId 처리 결과를 조회해 반환한다. */
    Optional<ClusterSyncSetting> findByClusterId(UUID clusterId);
}
