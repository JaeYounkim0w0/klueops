package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.job.AsyncJob;

import java.util.List;
import java.util.UUID;

public interface GetJobStatusUseCase {

    /** GetJobStatusUseCase의 getJob 처리 결과를 조회해 반환한다. */
    AsyncJob getJob(UUID jobId);

    /** GetJobStatusUseCase의 listRecentJobs 처리 결과를 조회해 반환한다. */
    List<AsyncJob> listRecentJobs();
}
