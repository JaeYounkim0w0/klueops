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

    /** AsyncJob 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AsyncJob(UUID id, AsyncJobType type, AsyncJobStatus status, Instant createdAt) {
        this(id, type, status, createdAt, null, null, null, null);
    }

    /** AsyncJob 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
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

    /** AsyncJob의 pending 처리에 필요한 업무 로직을 수행한다. */
    public static AsyncJob pending(AsyncJobType type) {
        return new AsyncJob(UUID.randomUUID(), type, AsyncJobStatus.PENDING, Instant.now());
    }

    /** AsyncJob의 markRunning 처리에 필요한 업무 로직을 수행한다. */
    public void markRunning(Instant now) {
        requireStatus(AsyncJobStatus.PENDING);
        this.status = AsyncJobStatus.RUNNING;
        this.startedAt = now;
    }

    /** AsyncJob의 markSucceeded 처리에 필요한 업무 로직을 수행한다. */
    public void markSucceeded(Instant now) {
        requireStatus(AsyncJobStatus.RUNNING);
        this.status = AsyncJobStatus.SUCCEEDED;
        this.completedAt = now;
    }

    /** AsyncJob의 markFailed 처리에 필요한 업무 로직을 수행한다. */
    public void markFailed(Instant now, String errorCode, String errorMessage) {
        requireStatus(AsyncJobStatus.RUNNING);
        this.status = AsyncJobStatus.FAILED;
        this.completedAt = now;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    /** AsyncJob의 markSubmissionFailed 처리에 필요한 업무 로직을 수행한다. */
    public void markSubmissionFailed(Instant now, String errorMessage) {
        requireStatus(AsyncJobStatus.PENDING);
        this.status = AsyncJobStatus.FAILED;
        this.completedAt = now;
        this.errorCode = "JOB_EXECUTOR_SATURATED";
        this.errorMessage = errorMessage;
    }

    /** AsyncJob의 markExecutionFailed 처리에 필요한 업무 로직을 수행한다. */
    public boolean markExecutionFailed(Instant now, String errorCode, String errorMessage) {
        if (status != AsyncJobStatus.PENDING && status != AsyncJobStatus.RUNNING) {
            return false;
        }
        // 커밋 후 큐 제출 실패와 worker 시작부 예외도 영구 PENDING으로 남지 않게 종결한다.
        this.status = AsyncJobStatus.FAILED;
        this.completedAt = now;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        return true;
    }

    /** AsyncJob의 markTimedOut 처리에 필요한 업무 로직을 수행한다. */
    public void markTimedOut(Instant now, String reason) {
        if (status != AsyncJobStatus.PENDING && status != AsyncJobStatus.RUNNING) {
            return;
        }
        this.status = AsyncJobStatus.TIMEOUT;
        this.completedAt = now;
        this.errorCode = "JOB_RUNTIME_EXCEEDED";
        this.errorMessage = reason;
    }

    /** AsyncJob의 markCanceled 처리에 필요한 업무 로직을 수행한다. */
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

    /** AsyncJob의 requireStatus 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void requireStatus(AsyncJobStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected status " + expected + " but was " + status);
        }
    }

    /** AsyncJob의 id 처리에 필요한 업무 로직을 수행한다. */
    public UUID id() {
        return id;
    }

    /** AsyncJob의 type 처리에 필요한 업무 로직을 수행한다. */
    public AsyncJobType type() {
        return type;
    }

    /** AsyncJob의 status 처리에 필요한 업무 로직을 수행한다. */
    public AsyncJobStatus status() {
        return status;
    }

    /** AsyncJob의 createdAt 처리에 필요한 데이터를 생성하거나 저장한다. */
    public Instant createdAt() {
        return createdAt;
    }

    /** AsyncJob의 startedAt 처리에 필요한 업무 로직을 수행한다. */
    public Instant startedAt() {
        return startedAt;
    }

    /** AsyncJob의 completedAt 처리에 필요한 업무 로직을 수행한다. */
    public Instant completedAt() {
        return completedAt;
    }

    /** AsyncJob의 errorCode 처리에 필요한 업무 로직을 수행한다. */
    public String errorCode() {
        return errorCode;
    }

    /** AsyncJob의 errorMessage 처리에 필요한 업무 로직을 수행한다. */
    public String errorMessage() {
        return errorMessage;
    }
}
