package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.job.AsyncJob;

import java.util.UUID;

public interface CancelJobUseCase {

    /** CancelJobUseCase의 cancelJob 처리 조건의 충족 여부를 판단한다. */
    AsyncJob cancelJob(UUID jobId, String actor, String requestId);
}
