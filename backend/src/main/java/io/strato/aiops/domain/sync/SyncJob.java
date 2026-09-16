package io.strato.aiops.domain.sync;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class SyncJob {

    private final UUID id;
    private final UUID asyncJobId;
    private final UUID clusterId;
    private final SyncType syncType;
    private SyncJobStatus status;
    private final String requestedBy;
    private int resourceCount;
    private int eventCount;
    private Instant startedAt;
    private Instant completedAt;
    private String errorMessage;
    private final Instant createdAt;

    /** SyncJob 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public SyncJob(UUID id, UUID asyncJobId, UUID clusterId, SyncType syncType, SyncJobStatus status, String requestedBy,
                   int resourceCount, int eventCount, Instant startedAt, Instant completedAt, String errorMessage, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.asyncJobId = Objects.requireNonNull(asyncJobId, "asyncJobId must not be null");
        this.clusterId = Objects.requireNonNull(clusterId, "clusterId must not be null");
        this.syncType = Objects.requireNonNull(syncType, "syncType must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy must not be null");
        this.resourceCount = resourceCount;
        this.eventCount = eventCount;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.errorMessage = errorMessage;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /** SyncJob의 pending 처리에 필요한 업무 로직을 수행한다. */
    public static SyncJob pending(UUID asyncJobId, UUID clusterId, SyncType syncType, String requestedBy) {
        return new SyncJob(UUID.randomUUID(), asyncJobId, clusterId, syncType, SyncJobStatus.PENDING, requestedBy, 0, 0, null, null, null, Instant.now());
    }

    /** SyncJob의 markRunning 처리에 필요한 업무 로직을 수행한다. */
    public void markRunning(Instant now) {
        requireStatus(SyncJobStatus.PENDING);
        this.status = SyncJobStatus.RUNNING;
        this.startedAt = now;
    }

    /** SyncJob의 markSucceeded 처리에 필요한 업무 로직을 수행한다. */
    public void markSucceeded(Instant now, int resourceCount, int eventCount) {
        requireStatus(SyncJobStatus.RUNNING);
        this.status = SyncJobStatus.SUCCEEDED;
        this.completedAt = now;
        this.resourceCount = resourceCount;
        this.eventCount = eventCount;
    }

    /** SyncJob의 markFailed 처리에 필요한 업무 로직을 수행한다. */
    public void markFailed(Instant now, String errorMessage) {
        requireStatus(SyncJobStatus.RUNNING);
        this.status = SyncJobStatus.FAILED;
        this.completedAt = now;
        this.errorMessage = errorMessage;
    }

    /** SyncJob의 markAbandoned 처리에 필요한 업무 로직을 수행한다. */
    public void markAbandoned(Instant now, String errorMessage) {
        if (status == SyncJobStatus.SUCCEEDED || status == SyncJobStatus.FAILED) {
            return;
        }
        this.status = SyncJobStatus.FAILED;
        this.completedAt = now;
        this.errorMessage = errorMessage;
    }

    /** SyncJob의 requireStatus 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void requireStatus(SyncJobStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected status " + expected + " but was " + status);
        }
    }

    /** SyncJob의 id 처리에 필요한 업무 로직을 수행한다. */
    public UUID id() {
        return id;
    }

    /** SyncJob의 asyncJobId 처리에 필요한 업무 로직을 수행한다. */
    public UUID asyncJobId() {
        return asyncJobId;
    }

    /** SyncJob의 clusterId 처리에 필요한 업무 로직을 수행한다. */
    public UUID clusterId() {
        return clusterId;
    }

    /** SyncJob의 syncType 처리의 핵심 작업 흐름을 실행한다. */
    public SyncType syncType() {
        return syncType;
    }

    /** SyncJob의 status 처리에 필요한 업무 로직을 수행한다. */
    public SyncJobStatus status() {
        return status;
    }

    /** SyncJob의 requestedBy 처리에 필요한 업무 로직을 수행한다. */
    public String requestedBy() {
        return requestedBy;
    }

    /** SyncJob의 resourceCount 처리에 필요한 업무 로직을 수행한다. */
    public int resourceCount() {
        return resourceCount;
    }

    /** SyncJob의 eventCount 처리에 필요한 업무 로직을 수행한다. */
    public int eventCount() {
        return eventCount;
    }

    /** SyncJob의 startedAt 처리에 필요한 업무 로직을 수행한다. */
    public Instant startedAt() {
        return startedAt;
    }

    /** SyncJob의 completedAt 처리에 필요한 업무 로직을 수행한다. */
    public Instant completedAt() {
        return completedAt;
    }

    /** SyncJob의 errorMessage 처리에 필요한 업무 로직을 수행한다. */
    public String errorMessage() {
        return errorMessage;
    }

    /** SyncJob의 createdAt 처리에 필요한 데이터를 생성하거나 저장한다. */
    public Instant createdAt() {
        return createdAt;
    }
}
