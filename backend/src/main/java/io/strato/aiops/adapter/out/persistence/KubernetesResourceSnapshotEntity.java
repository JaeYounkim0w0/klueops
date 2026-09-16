package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kubernetes_resource_snapshots")
class KubernetesResourceSnapshotEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID clusterId;

    @Column(nullable = false)
    private UUID syncJobId;

    private String namespace;

    @Column(nullable = false)
    private String resourceType;

    @Column(nullable = false)
    private String resourceName;

    private String resourceUid;

    private String status;

    @Column(nullable = false, columnDefinition = "text")
    private String summaryJson;

    @Column(columnDefinition = "text")
    private String rawJson;

    @Column(nullable = false)
    private boolean truncated;

    @Column(nullable = false)
    private Instant collectedAt;

    /** KubernetesResourceSnapshotEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    protected KubernetesResourceSnapshotEntity() {
    }

    /** KubernetesResourceSnapshotEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    private KubernetesResourceSnapshotEntity(UUID id, UUID clusterId, UUID syncJobId, String namespace, String resourceType,
                                             String resourceName, String resourceUid, String status, String summaryJson,
                                             String rawJson, boolean truncated, Instant collectedAt) {
        this.id = id;
        this.clusterId = clusterId;
        this.syncJobId = syncJobId;
        this.namespace = namespace;
        this.resourceType = resourceType;
        this.resourceName = resourceName;
        this.resourceUid = resourceUid;
        this.status = status;
        this.summaryJson = summaryJson;
        this.rawJson = rawJson;
        this.truncated = truncated;
        this.collectedAt = collectedAt;
    }

    /** KubernetesResourceSnapshotEntity의 fromDomain 처리 데이터를 필요한 표현으로 변환한다. */
    static KubernetesResourceSnapshotEntity fromDomain(KubernetesResourceSnapshot snapshot) {
        return new KubernetesResourceSnapshotEntity(
                snapshot.id(),
                snapshot.clusterId(),
                snapshot.syncJobId(),
                snapshot.namespace(),
                snapshot.resourceType(),
                snapshot.resourceName(),
                snapshot.resourceUid(),
                snapshot.status(),
                snapshot.summaryJson(),
                snapshot.rawJson(),
                snapshot.truncated(),
                snapshot.collectedAt()
        );
    }

    /** KubernetesResourceSnapshotEntity의 toDomain 처리 데이터를 필요한 표현으로 변환한다. */
    KubernetesResourceSnapshot toDomain() {
        return new KubernetesResourceSnapshot(id, clusterId, syncJobId, namespace, resourceType, resourceName, resourceUid, status, summaryJson, rawJson, truncated, collectedAt);
    }
}
