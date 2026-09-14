package io.strato.aiops.application.port.in;

import java.time.Instant;
import java.util.UUID;

public record KubernetesResourceManifestResult(
        UUID clusterId,
        String namespace,
        String resourceType,
        String resourceName,
        String manifestYaml,
        boolean secretRedacted,
        Instant collectedAt,
        String source,
        String fallbackReason
) {
}
