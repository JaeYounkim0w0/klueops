package io.strato.aiops.adapter.out.async;

import io.strato.aiops.application.port.out.AnalysisExecutorPort;
import io.strato.aiops.application.service.AnalysisApplicationService;
import io.strato.aiops.application.service.JobSubmissionFailureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SpringTaskExecutorAnalysisAdapter implements AnalysisExecutorPort {

    private static final Logger log = LoggerFactory.getLogger(SpringTaskExecutorAnalysisAdapter.class);

    private final TaskExecutor taskExecutor;
    private final AnalysisApplicationService analysisApplicationService;
    private final JobSubmissionFailureService jobSubmissionFailureService;

    public SpringTaskExecutorAnalysisAdapter(
            @Qualifier("analysisJobExecutor") TaskExecutor taskExecutor,
            @Lazy AnalysisApplicationService analysisApplicationService,
            JobSubmissionFailureService jobSubmissionFailureService
    ) {
        this.taskExecutor = taskExecutor;
        this.analysisApplicationService = analysisApplicationService;
        this.jobSubmissionFailureService = jobSubmissionFailureService;
    }

    @Override
    public void submitAnalysis(UUID asyncJobId) {
        log.info("Submitting AI analysis job {}", asyncJobId);
        try {
            taskExecutor.execute(() -> {
            try {
                log.info("Starting AI analysis job worker {}", asyncJobId);
                analysisApplicationService.runAnalysisJob(asyncJobId);
                log.info("Finished AI analysis job worker {}", asyncJobId);
            } catch (RuntimeException exception) {
                log.error("AI analysis job worker failed before normal job status handling. jobId={}", asyncJobId, exception);
                throw exception;
            }
            });
        } catch (TaskRejectedException exception) {
            jobSubmissionFailureService.markExecutorSaturated(asyncJobId, "AI analysis");
            throw exception;
        }
    }
}
