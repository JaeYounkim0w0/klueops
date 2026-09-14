package io.strato.aiops.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClusterResourceLogTargetsResult(
        UUID clusterId,
        String namespace,
        String resourceType,
        String resourceName,
        boolean supported,
        String unavailableReason,
        List<PodTarget> pods
) {
    public record PodTarget(
            String podName,
            String phase,
            Instant startedAt,
            List<ContainerTarget> containers
    ) {
    }

    public record ContainerTarget(
            String containerName,
            boolean ready,
            int restartCount,
            String state,
            boolean initContainer
    ) {
    }
}
