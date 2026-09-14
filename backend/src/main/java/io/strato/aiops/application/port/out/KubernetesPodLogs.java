package io.strato.aiops.application.port.out;

import java.time.Instant;
import java.util.List;

public record KubernetesPodLogs(
        String namespace,
        String podName,
        int tailLines,
        List<ContainerLog> containers,
        Instant collectedAt
) {
    public record ContainerLog(
            String containerName,
            String log,
            boolean truncated
    ) {
    }
}
