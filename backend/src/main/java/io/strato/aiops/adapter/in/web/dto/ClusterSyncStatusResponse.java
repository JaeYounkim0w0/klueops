package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.ClusterSyncStatusResult;
import io.strato.aiops.domain.sync.SyncJobStatus;
import io.strato.aiops.domain.sync.SyncType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Latest cluster sync status")
public record ClusterSyncStatusResponse(
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
    /** ClusterSyncStatusResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
    public static ClusterSyncStatusResponse from(ClusterSyncStatusResult result) {
        return new ClusterSyncStatusResponse(
                result.syncJobId(),
                result.asyncJobId(),
                result.clusterId(),
                result.syncType(),
                result.status(),
                result.resourceCount(),
                result.eventCount(),
                result.startedAt(),
                result.completedAt(),
                result.errorMessage(),
                result.createdAt()
        );
    }
}
