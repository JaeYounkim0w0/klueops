package io.strato.aiops.application.port.out;

import java.time.Instant;

public record KubernetesResourceLogSnapshot(
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
