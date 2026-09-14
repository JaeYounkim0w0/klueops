package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.domain.sync.KubernetesEventSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Kubernetes event snapshot")
public record KubernetesEventSnapshotResponse(
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
    public static KubernetesEventSnapshotResponse from(KubernetesEventSnapshot event) {
        return new KubernetesEventSnapshotResponse(
                event.id(),
                event.clusterId(),
                event.syncJobId(),
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
}
