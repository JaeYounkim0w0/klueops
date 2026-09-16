package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.KubernetesNamespaceSummary;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Kubernetes namespace summary")
public record KubernetesNamespaceResponse(
        String name,
        String status
) {
    /** KubernetesNamespaceResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
    public static KubernetesNamespaceResponse from(KubernetesNamespaceSummary namespace) {
        return new KubernetesNamespaceResponse(namespace.name(), namespace.status());
    }
}
