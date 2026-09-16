package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.KubernetesNodeSummary;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Kubernetes node summary")
public record KubernetesNodeResponse(
        String name,
        String status,
        String kubernetesVersion,
        String osImage,
        String containerRuntimeVersion
) {
    /** KubernetesNodeResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
    public static KubernetesNodeResponse from(KubernetesNodeSummary node) {
        return new KubernetesNodeResponse(
                node.name(),
                node.status(),
                node.kubernetesVersion(),
                node.osImage(),
                node.containerRuntimeVersion()
        );
    }
}
