package io.product.aiops.application.port.out;

public record KubernetesNamespace(
        String name,
        String status
) {
}
