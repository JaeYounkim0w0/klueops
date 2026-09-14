package io.strato.aiops.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApplicationRollbackPreviewResult(
        UUID applicationId,
        UUID clusterId,
        String namespace,
        String deploymentName,
        String currentRevision,
        String targetRevision,
        boolean executable,
        String reason,
        String confirmationText,
        String currentState,
        String targetState,
        List<ApplicationRollbackRevisionResult> revisions,
        Instant plannedAt
) {
}
