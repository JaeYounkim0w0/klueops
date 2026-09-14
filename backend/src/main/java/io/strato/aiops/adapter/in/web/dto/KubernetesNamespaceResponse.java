package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.KubernetesNamespaceSummary;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Kubernetes namespace summary")
public record KubernetesNamespaceResponse(
        String name,
        String status
) {
    public static KubernetesNamespaceResponse from(KubernetesNamespaceSummary namespace) {
        return new KubernetesNamespaceResponse(namespace.name(), namespace.status());
    }
}
