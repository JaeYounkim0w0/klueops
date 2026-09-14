package io.strato.aiops.application.port.in;

import java.util.UUID;

public record StartCommandExecutionCommand(
        UUID clusterId,
        UUID sourceAnalysisId,
        String namespace,
        String command,
        String manifest,
        boolean confirmed,
        String actor,
        String requestId
) {
}
