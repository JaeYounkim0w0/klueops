package io.product.aiops.application.port.out;

public record KubernetesNode(
        String name,
        String status,
        String kubernetesVersion,
        String osImage,
        String containerRuntimeVersion
) {
}
