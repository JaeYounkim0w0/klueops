package io.strato.aiops.domain.applicationdelivery;

import java.time.Instant;
import java.util.UUID;

public record ApplicationRelease(UUID id, UUID applicationId, int revision, UUID chartVersionId,
                                 UUID valuesRevisionId, String manifestSha256, String status,
                                 String createdBy, Instant createdAt) {
}
