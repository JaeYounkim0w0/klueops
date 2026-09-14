package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.domain.job.AsyncJob;
import io.strato.aiops.domain.job.AsyncJobStatus;
import io.strato.aiops.domain.job.AsyncJobType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Async job status response")
public record JobResponse(
        @Schema(description = "Job ID") UUID id,
        @Schema(description = "Job type") AsyncJobType type,
        @Schema(description = "Job status") AsyncJobStatus status,
        @Schema(description = "Job creation time") Instant createdAt,
        @Schema(description = "Job start time") Instant startedAt,
        @Schema(description = "Job completion time") Instant completedAt,
        @Schema(description = "Error code") String errorCode,
        @Schema(description = "Error message") String errorMessage
) {
    public static JobResponse from(AsyncJob job) {
        return new JobResponse(
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
}

