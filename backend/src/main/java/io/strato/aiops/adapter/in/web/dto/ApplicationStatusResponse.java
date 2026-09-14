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
    public static ApplicationStatusResponse from(ApplicationStatusResult result) {
        return new ApplicationStatusResponse(result.applicationId(), result.clusterId(), result.namespace(), result.name(),
                result.status(), result.lastSyncedAt(), result.lastSyncStatus(), result.lastSyncError());
    }
}
