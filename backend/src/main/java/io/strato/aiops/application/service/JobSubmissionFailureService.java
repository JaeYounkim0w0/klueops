package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.AsyncJobRepositoryPort;
import io.strato.aiops.domain.job.AsyncJob;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class JobSubmissionFailureService {

    private final AsyncJobRepositoryPort asyncJobRepositoryPort;

    public JobSubmissionFailureService(AsyncJobRepositoryPort asyncJobRepositoryPort) {
        this.asyncJobRepositoryPort = asyncJobRepositoryPort;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markExecutorSaturated(UUID jobId, String workload) {
        AsyncJob job = asyncJobRepositoryPort.findById(jobId).orElse(null);
        if (job == null || job.status().isTerminal()) {
            return;
        }
        if (job.startedAt() == null) {
            job.markSubmissionFailed(Instant.now(), workload + " executor queue is full; retry the operation later");
            asyncJobRepositoryPort.save(job);
        }
    }
}
