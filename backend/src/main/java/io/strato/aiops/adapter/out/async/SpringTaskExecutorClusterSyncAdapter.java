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

    public SpringTaskExecutorClusterSyncAdapter(
            @Qualifier("clusterSyncExecutor") TaskExecutor taskExecutor,
            ClusterSyncWorker clusterSyncWorker,
            JobSubmissionFailureService jobSubmissionFailureService
    ) {
        this.taskExecutor = taskExecutor;
        this.clusterSyncWorker = clusterSyncWorker;
        this.jobSubmissionFailureService = jobSubmissionFailureService;
    }

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
