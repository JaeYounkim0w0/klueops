package io.strato.aiops.application.port.out;

import java.util.UUID;

public interface ApplicationDeploymentExecutorPort {
    /** ApplicationDeploymentExecutorPort의 submit 처리 계약을 정의한다. */
    void submit(UUID planId, UUID tenantId, UUID applicationId, UUID jobId, UUID operationId);
    /** ApplicationDeploymentExecutorPort의 submitRollback 처리 계약을 정의한다. */
    void submitRollback(UUID tenantId, UUID applicationId, int revision, UUID jobId, UUID operationId, String actor);
    /** ApplicationDeploymentExecutorPort의 submitUninstall 처리 계약을 정의한다. */
    void submitUninstall(UUID tenantId, UUID applicationId, UUID jobId, UUID operationId, String actor,
                         boolean preservePvcs, boolean preserveDns, boolean preserveTls);
}
