package io.product.aiops.application.port.in;

public record KubernetesNamespaceSummary(
        String name,
        String status
) {
}
