package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.PodLogsResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PodLogsResponse(
        UUID clusterId,
        String namespace,
        String podName,
        int tailLines,
        List<PodLogsResult.ContainerLogResult> containers,
        Instant collectedAt
) {
    public static PodLogsResponse from(PodLogsResult result) {
        return new PodLogsResponse(
                result.clusterId(),
                result.namespace(),
                result.podName(),
                result.tailLines(),
                result.containers(),
                result.collectedAt()
        );
    }
}
