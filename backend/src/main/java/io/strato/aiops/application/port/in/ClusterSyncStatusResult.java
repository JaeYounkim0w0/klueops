package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.sync.SyncJobStatus;
import io.strato.aiops.domain.sync.SyncType;

import java.time.Instant;
import java.util.UUID;

public record ClusterSyncStatusResult(
        UUID syncJobId,
        UUID asyncJobId,
        UUID clusterId,
        SyncType syncType,
        SyncJobStatus status,
        int resourceCount,
        int eventCount,
        Instant startedAt,
        Instant completedAt,
        String errorMessage,
        Instant createdAt
) {
}
