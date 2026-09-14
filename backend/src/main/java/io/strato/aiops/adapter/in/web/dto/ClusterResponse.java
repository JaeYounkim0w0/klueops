package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.domain.cluster.ClusterEnvironment;
import io.strato.aiops.domain.cluster.ClusterProvider;
import io.strato.aiops.domain.cluster.ClusterStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Cluster response")
public record ClusterResponse(
        @Schema(description = "Cluster ID") UUID id,
        @Schema(description = "Owning tenant ID") UUID tenantId,
        @Schema(description = "Owning workspace ID") UUID workspaceId,
        @Schema(description = "Cluster display name") String name,
        @Schema(description = "Cluster description") String description,
        @Schema(description = "Cluster environment") ClusterEnvironment environment,
        @Schema(description = "Cluster provider") ClusterProvider provider,
        @Schema(description = "Region or location") String region,
        @Schema(description = "Cluster status") ClusterStatus status,
        @Schema(description = "Creator") String createdBy,
        @Schema(description = "Creation time") Instant createdAt,
        @Schema(description = "Last update time") Instant updatedAt
) {
    public static ClusterResponse from(Cluster cluster) {
        return new ClusterResponse(
                cluster.id(),
                cluster.tenantId(),
                cluster.workspaceId(),
                cluster.name(),
                cluster.description(),
                cluster.environment(),
                cluster.provider(),
                cluster.region(),
                cluster.status(),
                cluster.createdBy(),
                cluster.createdAt(),
                cluster.updatedAt()
        );
    }
}
