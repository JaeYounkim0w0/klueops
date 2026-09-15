package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.applicationdelivery.DeploymentPlan;
import io.strato.aiops.domain.applicationdelivery.ApplicationRelease;
import io.strato.aiops.domain.applicationdelivery.ReleaseOperation;
import io.strato.aiops.domain.applicationdelivery.ApplicationEndpoint;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationLifecycleRepositoryPort {
    DeploymentPlan savePlan(DeploymentPlan plan);
    Optional<DeploymentPlan> findPlan(UUID tenantId, UUID planId);
    boolean consumePlan(UUID planId, Instant consumedAt);
    ReleaseOperation saveOperation(ReleaseOperation operation);
    List<ReleaseOperation> findOperations(UUID tenantId, UUID applicationId, int limit);
    ApplicationRelease saveRelease(ApplicationRelease release);
    List<ApplicationRelease> findReleases(UUID tenantId, UUID applicationId, int limit);
    ApplicationEndpoint saveEndpoint(ApplicationEndpoint endpoint);
    List<ApplicationEndpoint> findEndpoints(UUID tenantId, UUID applicationId);
    void deleteApplicationGraph(UUID applicationId);

    default void recoverTimedOutOperation(UUID jobId, Instant completedAt, String errorMessage) {
        // Phase 1 저장소 구현과 테스트 대역은 Application Delivery 이력이 없을 수 있다.
    }

    default int recoverOrphanedOperations(Instant completedAt) {
        // Application Delivery를 사용하지 않는 저장소 구현은 복구할 작업이 없다.
        return 0;
    }
}
