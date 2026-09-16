package io.strato.aiops.adapter.out.async;

import io.strato.aiops.application.port.out.ClusterSyncExecutorPort;
import io.strato.aiops.application.service.ClusterSyncWorker;
import io.strato.aiops.application.service.JobSubmissionFailureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SpringTaskExecutorClusterSyncAdapter implements ClusterSyncExecutorPort {

    private static final Logger log = LoggerFactory.getLogger(SpringTaskExecutorClusterSyncAdapter.class);

    private final TaskExecutor taskExecutor;
    private final ClusterSyncWorker clusterSyncWorker;
    private final JobSubmissionFailureService jobSubmissionFailureService;

    /** SpringTaskExecutorClusterSyncAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public SpringTaskExecutorClusterSyncAdapter(
            @Qualifier("clusterSyncExecutor") TaskExecutor taskExecutor,
            ClusterSyncWorker clusterSyncWorker,
            JobSubmissionFailureService jobSubmissionFailureService
    ) {
        this.taskExecutor = taskExecutor;
        this.clusterSyncWorker = clusterSyncWorker;
        this.jobSubmissionFailureService = jobSubmissionFailureService;
    }

    /** SpringTaskExecutorClusterSyncAdapter의 submitClusterSync 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public void submitClusterSync(UUID asyncJobId) {
        log.info("Submitting cluster sync job {}", asyncJobId);
        try {
            taskExecutor.execute(() -> {
            try {
                log.info("Starting cluster sync job worker {}", asyncJobId);
                clusterSyncWorker.runClusterSync(asyncJobId);
                log.info("Finished cluster sync job worker {}", asyncJobId);
            } catch (RuntimeException exception) {
                log.error("Cluster sync job worker failed before normal job status handling. jobId={}", asyncJobId, exception);
                throw exception;
            }
            });
        } catch (TaskRejectedException exception) {
            jobSubmissionFailureService.markExecutorSaturated(asyncJobId, "Cluster synchronization");
            throw exception;
        }
    }
}
