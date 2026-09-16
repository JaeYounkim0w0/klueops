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
    /** ApplicationLifecycleRepositoryPort의 savePlan 처리에 필요한 데이터를 생성하거나 저장한다. */
    DeploymentPlan savePlan(DeploymentPlan plan);
    /** ApplicationLifecycleRepositoryPort의 findPlan 처리 결과를 조회해 반환한다. */
    Optional<DeploymentPlan> findPlan(UUID tenantId, UUID planId);
    /** ApplicationLifecycleRepositoryPort의 consumePlan 처리 계약을 정의한다. */
    boolean consumePlan(UUID planId, Instant consumedAt);
    /** ApplicationLifecycleRepositoryPort의 saveOperation 처리에 필요한 데이터를 생성하거나 저장한다. */
    ReleaseOperation saveOperation(ReleaseOperation operation);
    /** ApplicationLifecycleRepositoryPort의 findOperations 처리 결과를 조회해 반환한다. */
    List<ReleaseOperation> findOperations(UUID tenantId, UUID applicationId, int limit);
    /** ApplicationLifecycleRepositoryPort의 saveRelease 처리에 필요한 데이터를 생성하거나 저장한다. */
    ApplicationRelease saveRelease(ApplicationRelease release);
    /** ApplicationLifecycleRepositoryPort의 findReleases 처리 결과를 조회해 반환한다. */
    List<ApplicationRelease> findReleases(UUID tenantId, UUID applicationId, int limit);
    /** ApplicationLifecycleRepositoryPort의 saveEndpoint 처리에 필요한 데이터를 생성하거나 저장한다. */
    ApplicationEndpoint saveEndpoint(ApplicationEndpoint endpoint);
    /** ApplicationLifecycleRepositoryPort의 findEndpoints 처리 결과를 조회해 반환한다. */
    List<ApplicationEndpoint> findEndpoints(UUID tenantId, UUID applicationId);
    /** ApplicationLifecycleRepositoryPort의 deleteApplicationGraph 처리 대상과 관련 상태를 안전하게 정리한다. */
    void deleteApplicationGraph(UUID applicationId);

    /** ApplicationLifecycleRepositoryPort의 recoverTimedOutOperation 처리에 필요한 업무 로직을 수행한다. */
    default void recoverTimedOutOperation(UUID jobId, Instant completedAt, String errorMessage) {
        // Phase 1 저장소 구현과 테스트 대역은 Application Delivery 이력이 없을 수 있다.
    }

    /** ApplicationLifecycleRepositoryPort의 recoverOrphanedOperations 처리에 필요한 업무 로직을 수행한다. */
    default int recoverOrphanedOperations(Instant completedAt) {
        // Application Delivery를 사용하지 않는 저장소 구현은 복구할 작업이 없다.
        return 0;
    }
}
