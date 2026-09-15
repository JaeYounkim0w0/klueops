package io.strato.aiops.application.port.out;

import java.util.UUID;

public interface ApplicationDeploymentExecutorPort {
    void submit(UUID planId, UUID tenantId, UUID applicationId, UUID jobId, UUID operationId);
    void submitRollback(UUID tenantId, UUID applicationId, int revision, UUID jobId, UUID operationId, String actor);
    void submitUninstall(UUID tenantId, UUID applicationId, UUID jobId, UUID operationId, String actor);
}
