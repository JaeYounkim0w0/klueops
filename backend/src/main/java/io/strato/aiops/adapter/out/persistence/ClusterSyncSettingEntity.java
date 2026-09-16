package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.cluster.ClusterSyncSetting;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cluster_sync_settings")
class ClusterSyncSettingEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID clusterId;

    @Column(nullable = false)
    private boolean autoSyncEnabled;

    @Column(nullable = false)
    private int syncIntervalSeconds;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** ClusterSyncSettingEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    protected ClusterSyncSettingEntity() {
    }

    /** ClusterSyncSettingEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    private ClusterSyncSettingEntity(UUID id, UUID clusterId, boolean autoSyncEnabled, int syncIntervalSeconds,
                                     Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.clusterId = clusterId;
        this.autoSyncEnabled = autoSyncEnabled;
        this.syncIntervalSeconds = syncIntervalSeconds;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** ClusterSyncSettingEntity의 fromDomain 처리 데이터를 필요한 표현으로 변환한다. */
    static ClusterSyncSettingEntity fromDomain(ClusterSyncSetting syncSetting) {
        return new ClusterSyncSettingEntity(
                syncSetting.id(),
                syncSetting.clusterId(),
                syncSetting.autoSyncEnabled(),
                syncSetting.syncIntervalSeconds(),
                syncSetting.createdAt(),
                syncSetting.updatedAt()
        );
    }

    /** ClusterSyncSettingEntity의 toDomain 처리 데이터를 필요한 표현으로 변환한다. */
    ClusterSyncSetting toDomain() {
        return new ClusterSyncSetting(id, clusterId, autoSyncEnabled, syncIntervalSeconds, createdAt, updatedAt);
    }
}

