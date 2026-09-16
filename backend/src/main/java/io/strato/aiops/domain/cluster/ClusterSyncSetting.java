package io.strato.aiops.domain.cluster;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ClusterSyncSetting {

    private static final int DEFAULT_SYNC_INTERVAL_SECONDS = 300;

    private final UUID id;
    private final UUID clusterId;
    private final boolean autoSyncEnabled;
    private final int syncIntervalSeconds;
    private final Instant createdAt;
    private final Instant updatedAt;

    /** ClusterSyncSetting 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public ClusterSyncSetting(UUID id, UUID clusterId, boolean autoSyncEnabled, int syncIntervalSeconds, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.clusterId = Objects.requireNonNull(clusterId, "clusterId must not be null");
        this.autoSyncEnabled = autoSyncEnabled;
        this.syncIntervalSeconds = syncIntervalSeconds;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /** ClusterSyncSetting의 create 처리에 필요한 데이터를 생성하거나 저장한다. */
    public static ClusterSyncSetting create(UUID clusterId, Boolean autoSyncEnabled, Integer syncIntervalSeconds) {
        Instant now = Instant.now();
        return new ClusterSyncSetting(
                UUID.randomUUID(),
                clusterId,
                autoSyncEnabled == null || autoSyncEnabled,
                syncIntervalSeconds == null ? DEFAULT_SYNC_INTERVAL_SECONDS : syncIntervalSeconds,
                now,
                now
        );
    }

    /** ClusterSyncSetting의 update 처리 대상의 상태를 갱신한다. */
    public ClusterSyncSetting update(Boolean autoSyncEnabled, Integer syncIntervalSeconds) {
        return new ClusterSyncSetting(
                id,
                clusterId,
                autoSyncEnabled == null ? this.autoSyncEnabled : autoSyncEnabled,
                syncIntervalSeconds == null ? this.syncIntervalSeconds : syncIntervalSeconds,
                createdAt,
                Instant.now()
        );
    }

    /** ClusterSyncSetting의 id 처리에 필요한 업무 로직을 수행한다. */
    public UUID id() {
        return id;
    }

    /** ClusterSyncSetting의 clusterId 처리에 필요한 업무 로직을 수행한다. */
    public UUID clusterId() {
        return clusterId;
    }

    /** ClusterSyncSetting의 autoSyncEnabled 처리에 필요한 업무 로직을 수행한다. */
    public boolean autoSyncEnabled() {
        return autoSyncEnabled;
    }

    /** ClusterSyncSetting의 syncIntervalSeconds 처리의 핵심 작업 흐름을 실행한다. */
    public int syncIntervalSeconds() {
        return syncIntervalSeconds;
    }

    /** ClusterSyncSetting의 createdAt 처리에 필요한 데이터를 생성하거나 저장한다. */
    public Instant createdAt() {
        return createdAt;
    }

    /** ClusterSyncSetting의 updatedAt 처리 대상의 상태를 갱신한다. */
    public Instant updatedAt() {
        return updatedAt;
    }
}
