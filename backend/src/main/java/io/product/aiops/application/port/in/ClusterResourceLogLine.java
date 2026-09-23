package io.product.aiops.application.port.in;

import java.time.Instant;

public record ClusterResourceLogLine(
        String podName,
        String containerName,
        String line,
        Instant observedAt
) {
}
