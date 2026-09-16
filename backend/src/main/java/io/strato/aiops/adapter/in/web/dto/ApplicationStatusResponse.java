package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.ApplicationStatusResult;
import io.strato.aiops.domain.application.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Application status")
public record ApplicationStatusResponse(
        UUID applicationId,
        UUID clusterId,
        String namespace,
        String name,
        ApplicationStatus status,
        Instant lastSyncedAt,
        String lastSyncStatus,
        String lastSyncError
) {
    /** ApplicationStatusResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
    public static ApplicationStatusResponse from(ApplicationStatusResult result) {
        return new ApplicationStatusResponse(result.applicationId(), result.clusterId(), result.namespace(), result.name(),
                result.status(), result.lastSyncedAt(), result.lastSyncStatus(), result.lastSyncError());
    }
}
