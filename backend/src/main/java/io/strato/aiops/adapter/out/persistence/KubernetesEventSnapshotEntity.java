package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.sync.KubernetesEventSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kubernetes_event_snapshots")
class KubernetesEventSnapshotEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID clusterId;

    @Column(nullable = false)
    private UUID syncJobId;

    private String namespace;

    private String involvedKind;

    private String involvedName;

    private String reason;

    private String type;

    @Column(length = 1000)
    private String message;

    private Instant eventTime;

    private Integer count;

    @Column(nullable = false)
    private Instant collectedAt;

    /** KubernetesEventSnapshotEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    protected KubernetesEventSnapshotEntity() {
    }

    /** KubernetesEventSnapshotEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    private KubernetesEventSnapshotEntity(UUID id, UUID clusterId, UUID syncJobId, String namespace, String involvedKind,
                                          String involvedName, String reason, String type, String message, Instant eventTime,
                                          Integer count, Instant collectedAt) {
        this.id = id;
        this.clusterId = clusterId;
        this.syncJobId = syncJobId;
        this.namespace = namespace;
        this.involvedKind = involvedKind;
        this.involvedName = involvedName;
        this.reason = reason;
        this.type = type;
        this.message = message;
        this.eventTime = eventTime;
        this.count = count;
        this.collectedAt = collectedAt;
    }

    /** KubernetesEventSnapshotEntity의 fromDomain 처리 데이터를 필요한 표현으로 변환한다. */
    static KubernetesEventSnapshotEntity fromDomain(KubernetesEventSnapshot event) {
        return new KubernetesEventSnapshotEntity(
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

    /** KubernetesEventSnapshotEntity의 toDomain 처리 데이터를 필요한 표현으로 변환한다. */
    KubernetesEventSnapshot toDomain() {
        return new KubernetesEventSnapshot(id, clusterId, syncJobId, namespace, involvedKind, involvedName, reason, type, message, eventTime, count, collectedAt);
    }
}
