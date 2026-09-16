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
    /** KubernetesEventSnapshot 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public KubernetesEventSnapshot {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(clusterId, "clusterId must not be null");
        Objects.requireNonNull(syncJobId, "syncJobId must not be null");
        Objects.requireNonNull(collectedAt, "collectedAt must not be null");
    }

    /** KubernetesEventSnapshot의 collected 처리의 핵심 작업 흐름을 실행한다. */
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
