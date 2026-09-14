package io.strato.aiops.application.port.out;

import java.time.Instant;
import java.util.List;

public record KubernetesResourceLogTargets(
        String namespace,
        String resourceType,
        String resourceName,
        boolean supported,
        String unavailableReason,
        List<PodTarget> pods
) {
    public record PodTarget(String podName, String phase, Instant startedAt, List<ContainerTarget> containers) {
    }

    public record ContainerTarget(String containerName, boolean ready, int restartCount, String state,
                                  boolean initContainer) {
    }
}
