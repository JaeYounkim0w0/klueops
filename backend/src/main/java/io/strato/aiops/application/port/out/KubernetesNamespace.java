package io.strato.aiops.application.port.out;

public record KubernetesNamespace(
        String name,
        String status
) {
}
