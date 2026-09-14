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

    public ClusterSyncSetting(UUID id, UUID clusterId, boolean autoSyncEnabled, int syncIntervalSeconds, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.clusterId = Objects.requireNonNull(clusterId, "clusterId must not be null");
        this.autoSyncEnabled = autoSyncEnabled;
        this.syncIntervalSeconds = syncIntervalSeconds;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

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

    public UUID id() {
        return id;
    }

    public UUID clusterId() {
        return clusterId;
    }

    public boolean autoSyncEnabled() {
        return autoSyncEnabled;
    }

    public int syncIntervalSeconds() {
        return syncIntervalSeconds;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
