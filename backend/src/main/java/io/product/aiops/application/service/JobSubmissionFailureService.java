package io.product.aiops.application.service;

import io.product.aiops.application.port.out.AsyncJobRepositoryPort;
import io.product.aiops.domain.job.AsyncJob;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class JobSubmissionFailureService {

    private final AsyncJobRepositoryPort asyncJobRepositoryPort;

    /** JobSubmissionFailureService 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public JobSubmissionFailureService(AsyncJobRepositoryPort asyncJobRepositoryPort) {
        this.asyncJobRepositoryPort = asyncJobRepositoryPort;
    }

    /** JobSubmissionFailureService의 markExecutorSaturated 처리에 필요한 업무 로직을 수행한다. */
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
