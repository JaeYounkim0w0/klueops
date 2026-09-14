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

    public static SyncJob pending(UUID asyncJobId, UUID clusterId, SyncType syncType, String requestedBy) {
        return new SyncJob(UUID.randomUUID(), asyncJobId, clusterId, syncType, SyncJobStatus.PENDING, requestedBy, 0, 0, null, null, null, Instant.now());
    }

    public void markRunning(Instant now) {
        requireStatus(SyncJobStatus.PENDING);
        this.status = SyncJobStatus.RUNNING;
        this.startedAt = now;
    }

    public void markSucceeded(Instant now, int resourceCount, int eventCount) {
        requireStatus(SyncJobStatus.RUNNING);
        this.status = SyncJobStatus.SUCCEEDED;
        this.completedAt = now;
        this.resourceCount = resourceCount;
        this.eventCount = eventCount;
    }

    public void markFailed(Instant now, String errorMessage) {
        requireStatus(SyncJobStatus.RUNNING);
        this.status = SyncJobStatus.FAILED;
        this.completedAt = now;
        this.errorMessage = errorMessage;
    }

    public void markAbandoned(Instant now, String errorMessage) {
        if (status == SyncJobStatus.SUCCEEDED || status == SyncJobStatus.FAILED) {
            return;
        }
        this.status = SyncJobStatus.FAILED;
        this.completedAt = now;
        this.errorMessage = errorMessage;
    }

    private void requireStatus(SyncJobStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected status " + expected + " but was " + status);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID asyncJobId() {
        return asyncJobId;
    }

    public UUID clusterId() {
        return clusterId;
    }

    public SyncType syncType() {
        return syncType;
    }

    public SyncJobStatus status() {
        return status;
    }

    public String requestedBy() {
        return requestedBy;
    }

    public int resourceCount() {
        return resourceCount;
    }

    public int eventCount() {
        return eventCount;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
