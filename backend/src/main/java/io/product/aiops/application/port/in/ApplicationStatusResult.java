package io.product.aiops.application.port.in;

import io.product.aiops.domain.application.ApplicationStatus;

import java.time.Instant;
import java.util.UUID;

public record ApplicationStatusResult(
        UUID applicationId,
        UUID clusterId,
        String namespace,
        String name,
        ApplicationStatus status,
        Instant lastSyncedAt,
        String lastSyncStatus,
        String lastSyncError
) {
}
