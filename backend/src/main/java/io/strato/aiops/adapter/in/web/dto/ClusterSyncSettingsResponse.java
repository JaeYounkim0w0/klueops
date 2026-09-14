package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.domain.cluster.ClusterSyncSetting;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Cluster sync settings")
public record ClusterSyncSettingsResponse(
        UUID id,
        UUID clusterId,
        boolean autoSyncEnabled,
        int syncIntervalSeconds,
        Instant createdAt,
        Instant updatedAt
) {
    public static ClusterSyncSettingsResponse from(ClusterSyncSetting syncSetting) {
        return new ClusterSyncSettingsResponse(
                syncSetting.id(),
                syncSetting.clusterId(),
                syncSetting.autoSyncEnabled(),
                syncSetting.syncIntervalSeconds(),
                syncSetting.createdAt(),
                syncSetting.updatedAt()
        );
    }
}
