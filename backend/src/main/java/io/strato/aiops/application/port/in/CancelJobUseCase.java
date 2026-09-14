package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.job.AsyncJob;

import java.util.UUID;

public interface CancelJobUseCase {

    AsyncJob cancelJob(UUID jobId, String actor, String requestId);
}
