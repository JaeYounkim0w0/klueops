package io.strato.aiops.domain.sync;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record KubernetesEventSnapshot(
        UUID id,
        UUID clusterId,
        UUID syncJobId,
        String namespace,
        String involvedKind,
        String involvedName,
        String reason,
        String type,
        String message,
        Instant eventTime,
        Integer count,
        Instant collectedAt
) {
    public KubernetesEventSnapshot {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(clusterId, "clusterId must not be null");
        Objects.requireNonNull(syncJobId, "syncJobId must not be null");
        Objects.requireNonNull(collectedAt, "collectedAt must not be null");
    }

    public static KubernetesEventSnapshot collected(UUID clusterId, UUID syncJobId, CollectedEvent event) {
        return new KubernetesEventSnapshot(
                UUID.randomUUID(),
                clusterId,
                syncJobId,
                event.namespace(),
                event.involvedKind(),
                event.involvedName(),
                event.reason(),
                event.type(),
                event.message(),
                event.eventTime(),
                event.count(),
                event.collectedAt()
        );
    }

    public record CollectedEvent(
            String namespace,
            String involvedKind,
            String involvedName,
            String reason,
            String type,
            String message,
            Instant eventTime,
            Integer count,
            Instant collectedAt
    ) {
    }
}
