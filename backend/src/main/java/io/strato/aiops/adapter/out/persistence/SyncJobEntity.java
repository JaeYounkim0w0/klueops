package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.sync.SyncJob;
import io.strato.aiops.domain.sync.SyncJobStatus;
import io.strato.aiops.domain.sync.SyncType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sync_jobs")
class SyncJobEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID asyncJobId;

    @Column(nullable = false)
    private UUID clusterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SyncType syncType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SyncJobStatus status;

    @Column(nullable = false)
    private String requestedBy;

    @Column(nullable = false)
    private int resourceCount;

    @Column(nullable = false)
    private int eventCount;

    private Instant startedAt;

    private Instant completedAt;

    @Column(length = 1000)
    private String errorMessage;

    @Column(nullable = false)
    private Instant createdAt;

    /** SyncJobEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    protected SyncJobEntity() {
    }

    /** SyncJobEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    private SyncJobEntity(UUID id, UUID asyncJobId, UUID clusterId, SyncType syncType, SyncJobStatus status, String requestedBy,
                          int resourceCount, int eventCount, Instant startedAt, Instant completedAt, String errorMessage, Instant createdAt) {
        this.id = id;
        this.asyncJobId = asyncJobId;
        this.clusterId = clusterId;
        this.syncType = syncType;
        this.status = status;
        this.requestedBy = requestedBy;
        this.resourceCount = resourceCount;
        this.eventCount = eventCount;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
    }

    /** SyncJobEntity의 fromDomain 처리 데이터를 필요한 표현으로 변환한다. */
    static SyncJobEntity fromDomain(SyncJob syncJob) {
        return new SyncJobEntity(
                syncJob.id(),
                syncJob.asyncJobId(),
                syncJob.clusterId(),
                syncJob.syncType(),
                syncJob.status(),
                syncJob.requestedBy(),
                syncJob.resourceCount(),
                syncJob.eventCount(),
                syncJob.startedAt(),
                syncJob.completedAt(),
                syncJob.errorMessage(),
                syncJob.createdAt()
        );
    }

    /** SyncJobEntity의 toDomain 처리 데이터를 필요한 표현으로 변환한다. */
    SyncJob toDomain() {
        return new SyncJob(id, asyncJobId, clusterId, syncType, status, requestedBy, resourceCount, eventCount, startedAt, completedAt, errorMessage, createdAt);
    }
}
