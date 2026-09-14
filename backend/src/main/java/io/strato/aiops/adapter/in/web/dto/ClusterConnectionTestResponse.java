package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.ClusterConnectionTestResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Kubernetes cluster connection test result")
public record ClusterConnectionTestResponse(
        @Schema(description = "Cluster identifier")
        UUID clusterId,

        @Schema(description = "Whether Kubernetes API was reachable")
        boolean reachable,

        @Schema(description = "Kubernetes version returned by the API server")
        String kubernetesVersion,

        @Schema(description = "Sample namespace names visible to the configured credential")
        List<String> namespaces,

        @Schema(description = "Safe connection test message")
        String message,

        @Schema(description = "Connection test timestamp")
        Instant checkedAt
) {
    public static ClusterConnectionTestResponse from(ClusterConnectionTestResult result) {
        return new ClusterConnectionTestResponse(
                result.clusterId(),
                result.reachable(),
                result.kubernetesVersion(),
                result.namespaces(),
                result.message(),
                result.checkedAt()
        );
    }
}
