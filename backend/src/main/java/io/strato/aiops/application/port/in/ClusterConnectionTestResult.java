package io.strato.aiops.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClusterConnectionTestResult(
        UUID clusterId,
        boolean reachable,
        String kubernetesVersion,
        List<String> namespaces,
        String message,
        Instant checkedAt
) {
}
