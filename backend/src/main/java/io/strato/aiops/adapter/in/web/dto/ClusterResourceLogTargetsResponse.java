package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.ClusterResourceLogTargetsResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClusterResourceLogTargetsResponse(
        UUID clusterId,
        String namespace,
        String resourceType,
        String resourceName,
        boolean supported,
        String unavailableReason,
        List<PodTargetResponse> pods
) {
    public static ClusterResourceLogTargetsResponse from(ClusterResourceLogTargetsResult result) {
        return new ClusterResourceLogTargetsResponse(result.clusterId(), result.namespace(), result.resourceType(),
                result.resourceName(), result.supported(), result.unavailableReason(), result.pods().stream()
                .map(pod -> new PodTargetResponse(pod.podName(), pod.phase(), pod.startedAt(), pod.containers().stream()
                        .map(container -> new ContainerTargetResponse(container.containerName(), container.ready(),
                                container.restartCount(), container.state(), container.initContainer()))
                        .toList()))
                .toList());
    }

    public record PodTargetResponse(String podName, String phase, Instant startedAt,
                                    List<ContainerTargetResponse> containers) {
    }

    public record ContainerTargetResponse(String containerName, boolean ready, int restartCount, String state,
                                          boolean initContainer) {
    }
}
