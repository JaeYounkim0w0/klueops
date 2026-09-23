package io.product.aiops.application.port.in;

public record KubernetesNodeSummary(
        String name,
        String status,
        String kubernetesVersion,
        String osImage,
        String containerRuntimeVersion
) {
}
