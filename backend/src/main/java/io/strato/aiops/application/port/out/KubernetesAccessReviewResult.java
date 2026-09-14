package io.strato.aiops.application.port.out;

public record KubernetesAccessReviewResult(
        boolean allowed,
        String verb,
        String resource,
        String subresource,
        String namespace,
        String reason
) {
}
