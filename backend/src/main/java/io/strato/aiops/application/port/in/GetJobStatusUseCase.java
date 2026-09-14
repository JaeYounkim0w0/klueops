package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.job.AsyncJob;

import java.util.List;
import java.util.UUID;

public interface GetJobStatusUseCase {

    AsyncJob getJob(UUID jobId);

    List<AsyncJob> listRecentJobs();
}
