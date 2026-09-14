package io.strato.aiops.domain.sync;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record KubernetesResourceSnapshot(
        UUID id,
        UUID clusterId,
        UUID syncJobId,
        String namespace,
        String resourceType,
        String resourceName,
        String resourceUid,
        String status,
        String summaryJson,
        String rawJson,
        boolean truncated,
        Instant collectedAt
) {
    public KubernetesResourceSnapshot {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(clusterId, "clusterId must not be null");
        Objects.requireNonNull(syncJobId, "syncJobId must not be null");
        Objects.requireNonNull(resourceType, "resourceType must not be null");
        Objects.requireNonNull(resourceName, "resourceName must not be null");
        Objects.requireNonNull(summaryJson, "summaryJson must not be null");
        Objects.requireNonNull(collectedAt, "collectedAt must not be null");
    }

    public static KubernetesResourceSnapshot collected(UUID clusterId, UUID syncJobId, CollectedResource resource) {
        return new KubernetesResourceSnapshot(
                UUID.randomUUID(),
                clusterId,
                syncJobId,
                resource.namespace(),
                resource.resourceType(),
                resource.resourceName(),
                resource.resourceUid(),
                resource.status(),
                resource.summaryJson(),
                resource.rawJson(),
                resource.truncated(),
                resource.collectedAt()
        );
    }

    public record CollectedResource(
            String namespace,
            String resourceType,
            String resourceName,
            String resourceUid,
            String status,
            String summaryJson,
            String rawJson,
            boolean truncated,
            Instant collectedAt
    ) {
    }
}
