package io.strato.aiops.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PodLogsResult(
        UUID clusterId,
        String namespace,
        String podName,
        int tailLines,
        List<ContainerLogResult> containers,
        Instant collectedAt
) {
    public record ContainerLogResult(
            String containerName,
            String log,
            boolean truncated
    ) {
    }
}
