package io.strato.aiops.adapter.out.async;

import io.strato.aiops.application.port.out.ApplicationDeploymentExecutorPort;
import io.strato.aiops.application.service.ApplicationDeliveryJobFailureService;
import io.strato.aiops.application.service.ApplicationDeliveryDeploymentService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SpringTaskExecutorApplicationDeploymentAdapter implements ApplicationDeploymentExecutorPort {
    private final AfterCommitTaskDispatcher dispatcher;
    private final ApplicationDeliveryDeploymentService service;
    private final ApplicationDeliveryJobFailureService failureService;

    /** SpringTaskExecutorApplicationDeploymentAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public SpringTaskExecutorApplicationDeploymentAdapter(
            @Qualifier("applicationDeliveryExecutor") TaskExecutor executor,
            @Lazy ApplicationDeliveryDeploymentService service,
            ApplicationDeliveryJobFailureService failureService) {
        this.dispatcher = new AfterCommitTaskDispatcher(executor);
        this.service = service;
        this.failureService = failureService;
    }

    /** SpringTaskExecutorApplicationDeploymentAdapter의 submit 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public void submit(UUID planId, UUID tenantId, UUID applicationId, UUID jobId, UUID operationId) {
        dispatcher.dispatch(
                () -> service.executeInstall(planId, tenantId, applicationId, jobId, operationId),
                exception -> failureService.failInstall(planId, tenantId, applicationId, jobId, operationId,
                        "HELM_EXECUTOR_SATURATED", exception),
                exception -> failureService.failInstall(planId, tenantId, applicationId, jobId, operationId,
                        "HELM_WORKER_FAILED", exception));
    }

    /** SpringTaskExecutorApplicationDeploymentAdapter의 submitRollback 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public void submitRollback(UUID tenantId, UUID applicationId, int revision, UUID jobId, UUID operationId, String actor) {
        dispatcher.dispatch(
                () -> service.executeRollback(tenantId, applicationId, revision, jobId, operationId, actor),
                exception -> failureService.failLifecycle(applicationId, jobId, operationId, "ROLLBACK", revision,
                        actor, "HELM_EXECUTOR_SATURATED", exception),
                exception -> failureService.failLifecycle(applicationId, jobId, operationId, "ROLLBACK", revision,
                        actor, "HELM_WORKER_FAILED", exception));
    }

    /** SpringTaskExecutorApplicationDeploymentAdapter의 submitUninstall 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public void submitUninstall(UUID tenantId, UUID applicationId, UUID jobId, UUID operationId, String actor,
                                boolean preservePvcs, boolean preserveDns, boolean preserveTls) {
        dispatcher.dispatch(
                () -> service.executeUninstall(tenantId, applicationId, jobId, operationId, actor,
                        preservePvcs, preserveDns, preserveTls),
                exception -> failureService.failLifecycle(applicationId, jobId, operationId, "UNINSTALL", null,
                        actor, "HELM_EXECUTOR_SATURATED", exception),
                exception -> failureService.failLifecycle(applicationId, jobId, operationId, "UNINSTALL", null,
                        actor, "HELM_WORKER_FAILED", exception));
    }
}
