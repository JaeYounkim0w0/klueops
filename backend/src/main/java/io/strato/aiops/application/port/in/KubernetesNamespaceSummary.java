package io.strato.aiops.application.port.in;

public record KubernetesNamespaceSummary(
        String name,
        String status
) {
}
