package io.strato.aiops.adapter.out.async;

import io.strato.aiops.application.port.out.ApplicationDeploymentExecutorPort;
import io.strato.aiops.application.service.ApplicationDeliveryDeploymentService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SpringTaskExecutorApplicationDeploymentAdapter implements ApplicationDeploymentExecutorPort {
    private final TaskExecutor executor;
    private final ApplicationDeliveryDeploymentService service;

    public SpringTaskExecutorApplicationDeploymentAdapter(
            @Qualifier("applicationDeliveryExecutor") TaskExecutor executor,
            @Lazy ApplicationDeliveryDeploymentService service) {
        this.executor = executor;
        this.service = service;
    }

    @Override
    public void submit(UUID planId, UUID tenantId, UUID applicationId, UUID jobId, UUID operationId) {
        executor.execute(() -> service.executeInstall(planId, tenantId, applicationId, jobId, operationId));
    }

    @Override
    public void submitRollback(UUID tenantId, UUID applicationId, int revision, UUID jobId, UUID operationId, String actor) {
        executor.execute(() -> service.executeRollback(tenantId, applicationId, revision, jobId, operationId, actor));
    }

    @Override
    public void submitUninstall(UUID tenantId, UUID applicationId, UUID jobId, UUID operationId, String actor) {
        executor.execute(() -> service.executeUninstall(tenantId, applicationId, jobId, operationId, actor));
    }
}
