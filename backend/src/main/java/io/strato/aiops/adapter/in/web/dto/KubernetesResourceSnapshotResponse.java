package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Kubernetes resource snapshot")
public record KubernetesResourceSnapshotResponse(
        UUID id,
        UUID clusterId,
        UUID syncJobId,
        String namespace,
        String resourceType,
        String resourceName,
        String resourceUid,
        String status,
        String summaryJson,
        boolean truncated,
        Instant collectedAt
) {
    public static KubernetesResourceSnapshotResponse from(KubernetesResourceSnapshot snapshot) {
        return new KubernetesResourceSnapshotResponse(
                snapshot.id(),
                snapshot.clusterId(),
                snapshot.syncJobId(),
                snapshot.namespace(),
                snapshot.resourceType(),
                snapshot.resourceName(),
                snapshot.resourceUid(),
                snapshot.status(),
                snapshot.summaryJson(),
                snapshot.truncated(),
                snapshot.collectedAt()
        );
    }
}
