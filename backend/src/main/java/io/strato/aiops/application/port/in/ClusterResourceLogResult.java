package io.strato.aiops.application.port.in;

import java.time.Instant;
import java.util.UUID;

public record ClusterResourceLogResult(
        UUID clusterId,
        String namespace,
        String resourceType,
        String resourceName,
        String podName,
        String containerName,
        int tailLines,
        boolean previous,
        String log,
        boolean truncated,
        Instant collectedAt
) {
}
