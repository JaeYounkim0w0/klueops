package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.ApplicationLifecycleRepositoryPort;
import io.strato.aiops.application.port.out.AsyncJobRepositoryPort;
import io.strato.aiops.application.port.out.ManagedApplicationRepositoryPort;
import io.strato.aiops.domain.application.ApplicationStatus;
import io.strato.aiops.domain.application.ManagedApplication;
import io.strato.aiops.domain.applicationdelivery.ApplicationEndpoint;
import io.strato.aiops.domain.applicationdelivery.ApplicationRelease;
import io.strato.aiops.domain.applicationdelivery.DeploymentPlan;
import io.strato.aiops.domain.applicationdelivery.ReleaseOperation;
import io.strato.aiops.domain.job.AsyncJob;
import io.strato.aiops.domain.job.AsyncJobStatus;
import io.strato.aiops.domain.job.AsyncJobType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationDeliveryJobFailureServiceTest {

    /** ApplicationDeliveryJobFailureServiceTest의 workerStartupFailureTerminatesJobApplicationAndOperation 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void workerStartupFailureTerminatesJobApplicationAndOperation() {
        AsyncJob job = new AsyncJob(UUID.randomUUID(), AsyncJobType.HELM_INSTALL, AsyncJobStatus.PENDING,
                Instant.parse("2026-09-16T02:29:59Z"));
        ManagedApplication application = ManagedApplication.helmChart(UUID.randomUUID(), "team-a", "postgres",
                "postgres", "postgres", "actor").withStatus(ApplicationStatus.DEPLOYING, null, null);
        InMemoryJobs jobs = new InMemoryJobs(job);
        InMemoryApplications applications = new InMemoryApplications(application);
        InMemoryLifecycle lifecycle = new InMemoryLifecycle();
        Clock clock = Clock.fixed(Instant.parse("2026-09-16T02:30:00Z"), ZoneOffset.UTC);
        ApplicationDeliveryJobFailureService service =
                new ApplicationDeliveryJobFailureService(jobs, applications, lifecycle, clock);
        UUID operationId = UUID.randomUUID();

        service.failInstall(UUID.randomUUID(), UUID.randomUUID(), application.id(), job.id(), operationId,
                "HELM_WORKER_FAILED", new IllegalStateException("worker crashed"));

        assertThat(jobs.saved.status()).isEqualTo(AsyncJobStatus.FAILED);
        assertThat(jobs.saved.errorCode()).isEqualTo("HELM_WORKER_FAILED");
        assertThat(applications.saved.status()).isEqualTo(ApplicationStatus.FAILED);
        assertThat(lifecycle.savedOperation.id()).isEqualTo(operationId);
        assertThat(lifecycle.savedOperation.status()).isEqualTo("FAILED");
        assertThat(lifecycle.savedOperation.errorMessage()).isEqualTo("worker crashed");
    }

    private static final class InMemoryJobs implements AsyncJobRepositoryPort {
        private final AsyncJob stored;
        private AsyncJob saved;

        /** InMemoryJobs 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
        private InMemoryJobs(AsyncJob stored) { this.stored = stored; }
        /** InMemoryJobs의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
        @Override public AsyncJob save(AsyncJob job) { this.saved = job; return job; }
        /** InMemoryJobs의 findById 처리 결과를 조회해 반환한다. */
        @Override public Optional<AsyncJob> findById(UUID jobId) { return Optional.of(stored); }
        /** InMemoryJobs의 findRecent 처리 결과를 조회해 반환한다. */
        @Override public List<AsyncJob> findRecent(int limit) { return List.of(stored); }
        /** InMemoryJobs의 findActiveCreatedBefore 처리 결과를 조회해 반환한다. */
        @Override public List<AsyncJob> findActiveCreatedBefore(Instant cutoff) { return List.of(); }
    }

    private static final class InMemoryApplications implements ManagedApplicationRepositoryPort {
        private final ManagedApplication stored;
        private ManagedApplication saved;

        /** InMemoryApplications 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
        private InMemoryApplications(ManagedApplication stored) { this.stored = stored; }
        /** InMemoryApplications의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
        @Override public ManagedApplication save(ManagedApplication application) { this.saved = application; return application; }
        /** InMemoryApplications의 findById 처리 결과를 조회해 반환한다. */
        @Override public Optional<ManagedApplication> findById(UUID applicationId) { return Optional.of(stored); }
        /** InMemoryApplications의 findRecent 처리 결과를 조회해 반환한다. */
        @Override public List<ManagedApplication> findRecent(int limit) { return List.of(stored); }
    }

    private static final class InMemoryLifecycle implements ApplicationLifecycleRepositoryPort {
        private ReleaseOperation savedOperation;

        /** InMemoryLifecycle의 savePlan 처리에 필요한 데이터를 생성하거나 저장한다. */
        @Override public DeploymentPlan savePlan(DeploymentPlan plan) { return plan; }
        /** InMemoryLifecycle의 findPlan 처리 결과를 조회해 반환한다. */
        @Override public Optional<DeploymentPlan> findPlan(UUID tenantId, UUID planId) { return Optional.empty(); }
        /** InMemoryLifecycle의 consumePlan 처리에 필요한 업무 로직을 수행한다. */
        @Override public boolean consumePlan(UUID planId, Instant consumedAt) { return false; }
        /** InMemoryLifecycle의 saveOperation 처리에 필요한 데이터를 생성하거나 저장한다. */
        @Override public ReleaseOperation saveOperation(ReleaseOperation operation) { savedOperation = operation; return operation; }
        /** InMemoryLifecycle의 findOperations 처리 결과를 조회해 반환한다. */
        @Override public List<ReleaseOperation> findOperations(UUID tenantId, UUID applicationId, int limit) { return List.of(); }
        /** InMemoryLifecycle의 saveRelease 처리에 필요한 데이터를 생성하거나 저장한다. */
        @Override public ApplicationRelease saveRelease(ApplicationRelease release) { return release; }
        /** InMemoryLifecycle의 findReleases 처리 결과를 조회해 반환한다. */
        @Override public List<ApplicationRelease> findReleases(UUID tenantId, UUID applicationId, int limit) { return List.of(); }
        /** InMemoryLifecycle의 saveEndpoint 처리에 필요한 데이터를 생성하거나 저장한다. */
        @Override public ApplicationEndpoint saveEndpoint(ApplicationEndpoint endpoint) { return endpoint; }
        /** InMemoryLifecycle의 findEndpoints 처리 결과를 조회해 반환한다. */
        @Override public List<ApplicationEndpoint> findEndpoints(UUID tenantId, UUID applicationId) { return List.of(); }
        /** InMemoryLifecycle의 deleteApplicationGraph 처리 대상과 관련 상태를 안전하게 정리한다. */
        @Override public void deleteApplicationGraph(UUID applicationId) { }
    }
}
