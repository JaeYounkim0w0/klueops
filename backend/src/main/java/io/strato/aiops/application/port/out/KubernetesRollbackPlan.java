package io.strato.aiops.application.port.out;

import java.time.Instant;

public record KubernetesRollbackPlan(
        String namespace,
        String deploymentName,
        String currentRevision,
        String targetRevision,
        boolean executable,
        String reason,
        String confirmationText,
        String currentState,
        String targetState,
        Instant plannedAt
) {
}
