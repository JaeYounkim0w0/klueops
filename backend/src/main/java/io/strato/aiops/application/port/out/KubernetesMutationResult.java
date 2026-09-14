package io.strato.aiops.application.port.out;

import java.time.Instant;

public record KubernetesMutationResult(
        String namespace,
        String resourceType,
        String resourceName,
        String action,
        String previousState,
        String nextState,
        Instant changedAt
) {
}
