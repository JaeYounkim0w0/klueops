package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.ApplicationLifecycleRepositoryPort;
import io.strato.aiops.application.port.out.AsyncJobRepositoryPort;
import io.strato.aiops.application.port.out.ManagedApplicationRepositoryPort;
import io.strato.aiops.domain.application.ApplicationStatus;
import io.strato.aiops.domain.applicationdelivery.DeploymentPlan;
import io.strato.aiops.domain.applicationdelivery.ReleaseOperation;
import io.strato.aiops.domain.job.AsyncJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class ApplicationDeliveryJobFailureService {
    private static final Logger log = LoggerFactory.getLogger(ApplicationDeliveryJobFailureService.class);

    private final AsyncJobRepositoryPort jobs;
    private final ManagedApplicationRepositoryPort applications;
    private final ApplicationLifecycleRepositoryPort lifecycle;
    private final Clock clock;

    /** ApplicationDeliveryJobFailureService 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public ApplicationDeliveryJobFailureService(AsyncJobRepositoryPort jobs,
                                                ManagedApplicationRepositoryPort applications,
                                                ApplicationLifecycleRepositoryPort lifecycle,
                                                Clock clock) {
        this.jobs = jobs;
        this.applications = applications;
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    /** ApplicationDeliveryJobFailureService의 failInstall 처리에 필요한 업무 로직을 수행한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failInstall(UUID planId, UUID tenantId, UUID applicationId, UUID jobId, UUID operationId,
                            String errorCode, RuntimeException exception) {
        DeploymentPlan plan = lifecycle.findPlan(tenantId, planId).orElse(null);
        String operationType = plan != null && plan.applicationId() != null ? "UPGRADE" : "INSTALL";
        String actor = plan == null ? "system" : plan.createdBy();
        fail(applicationId, jobId, operationId, operationType, null, actor, errorCode, exception);
    }

    /** ApplicationDeliveryJobFailureService의 failLifecycle 처리에 필요한 업무 로직을 수행한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failLifecycle(UUID applicationId, UUID jobId, UUID operationId, String operationType,
                              Integer revision, String actor, String errorCode, RuntimeException exception) {
        fail(applicationId, jobId, operationId, operationType, revision, actor, errorCode, exception);
    }

    /** ApplicationDeliveryJobFailureService의 fail 처리에 필요한 업무 로직을 수행한다. */
    private void fail(UUID applicationId, UUID jobId, UUID operationId, String operationType, Integer revision,
                      String actor, String errorCode, RuntimeException exception) {
        Instant completed = clock.instant();
        String message = bounded(exception.getMessage());
        AsyncJob job = jobs.findById(jobId).orElse(null);
        if (job != null && !job.markExecutionFailed(completed, errorCode, message)) {
            return;
        }
        if (job != null) jobs.save(job);

        var application = applications.findById(applicationId).orElse(null);
        if (application == null) {
            log.error("Application Delivery worker failed without persisted application. applicationId={}, jobId={}",
                    applicationId, jobId);
            return;
        }
        // 비동기 예외가 스레드 로그에만 남지 않도록 화면 상태와 operation을 같은 새 transaction에서 종결한다.
        applications.save(application.withStatus(ApplicationStatus.FAILED, errorCode, message));
        lifecycle.saveOperation(new ReleaseOperation(operationId, applicationId, jobId, operationType, "FAILED",
                revision, null, message, actor, completed, completed));
    }

    /** ApplicationDeliveryJobFailureService의 bounded 처리에 필요한 업무 로직을 수행한다. */
    private String bounded(String value) {
        String safe = value == null || value.isBlank() ? "Application Delivery worker failed" : value;
        return safe.length() <= 1000 ? safe : safe.substring(0, 1000);
    }
}
