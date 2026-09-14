package io.strato.aiops.domain.job;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AsyncJob {

    private final UUID id;
    private final AsyncJobType type;
    private AsyncJobStatus status;
    private final Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private String errorCode;
    private String errorMessage;

    public AsyncJob(UUID id, AsyncJobType type, AsyncJobStatus status, Instant createdAt) {
        this(id, type, status, createdAt, null, null, null, null);
    }

    public AsyncJob(UUID id, AsyncJobType type, AsyncJobStatus status, Instant createdAt, Instant startedAt,
                    Instant completedAt, String errorCode, String errorMessage) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static AsyncJob pending(AsyncJobType type) {
        return new AsyncJob(UUID.randomUUID(), type, AsyncJobStatus.PENDING, Instant.now());
    }

    public void markRunning(Instant now) {
        requireStatus(AsyncJobStatus.PENDING);
        this.status = AsyncJobStatus.RUNNING;
        this.startedAt = now;
    }

    public void markSucceeded(Instant now) {
        requireStatus(AsyncJobStatus.RUNNING);
        this.status = AsyncJobStatus.SUCCEEDED;
        this.completedAt = now;
    }

    public void markFailed(Instant now, String errorCode, String errorMessage) {
        requireStatus(AsyncJobStatus.RUNNING);
        this.status = AsyncJobStatus.FAILED;
        this.completedAt = now;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public void markSubmissionFailed(Instant now, String errorMessage) {
        requireStatus(AsyncJobStatus.PENDING);
        this.status = AsyncJobStatus.FAILED;
        this.completedAt = now;
        this.errorCode = "JOB_EXECUTOR_SATURATED";
        this.errorMessage = errorMessage;
    }

    public void markTimedOut(Instant now, String reason) {
        if (status != AsyncJobStatus.PENDING && status != AsyncJobStatus.RUNNING) {
            return;
        }
        this.status = AsyncJobStatus.TIMEOUT;
        this.completedAt = now;
        this.errorCode = "JOB_RUNTIME_EXCEEDED";
        this.errorMessage = reason;
    }

    public void markCanceled(Instant now, String reason) {
        if (status == AsyncJobStatus.SUCCEEDED || status == AsyncJobStatus.FAILED
                || status == AsyncJobStatus.CANCELED || status == AsyncJobStatus.TIMEOUT) {
            return;
        }
        this.status = AsyncJobStatus.CANCELED;
        this.completedAt = now;
        this.errorCode = "JOB_CANCELED";
        this.errorMessage = reason;
    }

    private void requireStatus(AsyncJobStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected status " + expected + " but was " + status);
        }
    }

    public UUID id() {
        return id;
    }

    public AsyncJobType type() {
        return type;
    }

    public AsyncJobStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public String errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
    }
}
