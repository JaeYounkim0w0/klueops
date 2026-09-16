package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.ClusterSyncSettingRepositoryPort;
import io.strato.aiops.domain.cluster.ClusterSyncSetting;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaClusterSyncSettingRepositoryAdapter implements ClusterSyncSettingRepositoryPort {

    private final ClusterSyncSettingJpaRepository clusterSyncSettingJpaRepository;

    /** JpaClusterSyncSettingRepositoryAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public JpaClusterSyncSettingRepositoryAdapter(ClusterSyncSettingJpaRepository clusterSyncSettingJpaRepository) {
        this.clusterSyncSettingJpaRepository = clusterSyncSettingJpaRepository;
    }

    /** JpaClusterSyncSettingRepositoryAdapter의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public ClusterSyncSetting save(ClusterSyncSetting syncSetting) {
        return clusterSyncSettingJpaRepository.save(ClusterSyncSettingEntity.fromDomain(syncSetting)).toDomain();
    }

    /** JpaClusterSyncSettingRepositoryAdapter의 findByClusterId 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<ClusterSyncSetting> findByClusterId(UUID clusterId) {
        return clusterSyncSettingJpaRepository.findByClusterId(clusterId)
                .map(ClusterSyncSettingEntity::toDomain);
    }
}
