package io.strato.aiops.domain.applicationdelivery;

import java.time.Instant;
import java.util.UUID;

public record ReleaseOperation(UUID id, UUID applicationId, UUID asyncJobId, String operationType,
                               String status, Integer releaseRevision, String outputSummary,
                               String errorMessage, String requestedBy, Instant requestedAt,
                               Instant completedAt) {
}
