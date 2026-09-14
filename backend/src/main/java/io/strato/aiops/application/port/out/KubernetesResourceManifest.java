package io.strato.aiops.application.port.out;

import java.time.Instant;

public record KubernetesResourceManifest(
        String namespace,
        String resourceType,
        String resourceName,
        String manifestYaml,
        boolean secretRedacted,
        Instant collectedAt
) {
}
