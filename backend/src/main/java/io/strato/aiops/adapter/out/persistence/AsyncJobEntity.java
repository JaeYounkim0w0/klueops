package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.job.AsyncJob;
import io.strato.aiops.domain.job.AsyncJobStatus;
import io.strato.aiops.domain.job.AsyncJobType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "async_jobs")
class AsyncJobEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AsyncJobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AsyncJobStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant startedAt;

    private Instant completedAt;

    private String errorCode;

    @Column(length = 1000)
    private String errorMessage;

    protected AsyncJobEntity() {
    }

    private AsyncJobEntity(UUID id, AsyncJobType type, AsyncJobStatus status, Instant createdAt, Instant startedAt,
                           Instant completedAt, String errorCode, String errorMessage) {
        this.id = id;
        this.type = type;
        this.status = status;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    static AsyncJobEntity fromDomain(AsyncJob job) {
        return new AsyncJobEntity(
                job.id(),
                job.type(),
                job.status(),
                job.createdAt(),
                job.startedAt(),
                job.completedAt(),
                job.errorCode(),
                job.errorMessage()
        );
    }

    AsyncJob toDomain() {
        return new AsyncJob(id, type, status, createdAt, startedAt, completedAt, errorCode, errorMessage);
    }
}
