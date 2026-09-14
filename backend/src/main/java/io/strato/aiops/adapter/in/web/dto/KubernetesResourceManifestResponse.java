package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.KubernetesResourceManifestResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Live Kubernetes resource manifest")
public record KubernetesResourceManifestResponse(
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
    public static KubernetesResourceManifestResponse from(KubernetesResourceManifestResult result) {
        return new KubernetesResourceManifestResponse(
                result.clusterId(),
                result.namespace(),
                result.resourceType(),
                result.resourceName(),
                result.manifestYaml(),
                result.secretRedacted(),
                result.collectedAt(),
                result.source(),
                result.fallbackReason()
        );
    }
}
