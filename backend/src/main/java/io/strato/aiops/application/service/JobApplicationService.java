package io.strato.aiops.application.service;

import io.strato.aiops.application.port.in.CancelJobUseCase;
import io.strato.aiops.application.port.in.GetJobStatusUseCase;
import io.strato.aiops.application.port.in.StartClusterSyncUseCase;
import io.strato.aiops.application.port.out.AsyncJobRepositoryPort;
import io.strato.aiops.application.port.out.AuditLogRepositoryPort;
import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.application.port.out.ClusterSyncExecutorPort;
import io.strato.aiops.application.port.out.SyncJobRepositoryPort;
import io.strato.aiops.domain.audit.AuditLog;
import io.strato.aiops.domain.job.AsyncJob;
import io.strato.aiops.domain.job.AsyncJobStatus;
import io.strato.aiops.domain.job.AsyncJobType;
import io.strato.aiops.domain.sync.SyncJob;
import io.strato.aiops.domain.sync.SyncJobStatus;
import io.strato.aiops.domain.sync.SyncType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class JobApplicationService implements GetJobStatusUseCase, StartClusterSyncUseCase, CancelJobUseCase {

    private static final int RECENT_JOB_LIMIT = 100;
    private static final Duration PENDING_SYNC_RESUBMIT_THRESHOLD = Duration.ofSeconds(5);

    private final AsyncJobRepositoryPort asyncJobRepositoryPort;
    private final ClusterRepositoryPort clusterRepositoryPort;
    private final SyncJobRepositoryPort syncJobRepositoryPort;
    private final ClusterSyncExecutorPort clusterSyncExecutorPort;
    private final AuditLogRepositoryPort auditLogRepositoryPort;

    /** JobApplicationService 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public JobApplicationService(AsyncJobRepositoryPort asyncJobRepositoryPort, ClusterRepositoryPort clusterRepositoryPort,
                                 SyncJobRepositoryPort syncJobRepositoryPort, ClusterSyncExecutorPort clusterSyncExecutorPort,
                                 AuditLogRepositoryPort auditLogRepositoryPort) {
        this.asyncJobRepositoryPort = asyncJobRepositoryPort;
        this.clusterRepositoryPort = clusterRepositoryPort;
        this.syncJobRepositoryPort = syncJobRepositoryPort;
        this.clusterSyncExecutorPort = clusterSyncExecutorPort;
        this.auditLogRepositoryPort = auditLogRepositoryPort;
    }

    /** JobApplicationService의 getJob 처리 결과를 조회해 반환한다. */
    @Override
    @Transactional(readOnly = true)
    public AsyncJob getJob(UUID jobId) {
        return asyncJobRepositoryPort.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Job not found: " + jobId));
    }

    /** JobApplicationService의 listRecentJobs 처리 결과를 조회해 반환한다. */
    @Override
    @Transactional(readOnly = true)
    public List<AsyncJob> listRecentJobs() {
        return asyncJobRepositoryPort.findRecent(RECENT_JOB_LIMIT);
    }

    /** JobApplicationService의 cancelJob 처리 조건의 충족 여부를 판단한다. */
    @Override
    @Transactional
    public AsyncJob cancelJob(UUID jobId, String actor, String requestId) {
        AsyncJob job = getJob(jobId);
        Instant canceledAt = Instant.now();
        job.markCanceled(canceledAt, "Canceled by " + actor);
        AsyncJob saved = asyncJobRepositoryPort.save(job);
        syncJobRepositoryPort.findByAsyncJobId(jobId)
                .ifPresent(syncJob -> {
                    syncJob.markAbandoned(canceledAt, "Linked async job canceled by " + actor);
                    syncJobRepositoryPort.save(syncJob);
                });
        auditLogRepositoryPort.save(AuditLog.create(
                "ASYNC_JOB_CANCELED",
                "JOB",
                jobId.toString(),
                actor,
                requestId
        ));
        return saved;
    }

    /** JobApplicationService의 startClusterSync 처리에 필요한 업무 로직을 수행한다. */
    @Override
    @Transactional
    public UUID startClusterSync(UUID clusterId, String actor, String requestId) {
        clusterRepositoryPort.lockById(clusterId);

        Optional<SyncJob> activeSyncJob = syncJobRepositoryPort.findLatestByClusterIdAndStatusIn(
                clusterId,
                EnumSet.of(SyncJobStatus.PENDING, SyncJobStatus.RUNNING)
        );
        if (activeSyncJob.isPresent()) {
            SyncJob syncJob = activeSyncJob.get();
            Optional<AsyncJob> linkedJob = asyncJobRepositoryPort.findById(syncJob.asyncJobId());
            if (linkedJob.isPresent() && isReusableSyncJob(linkedJob.get())) {
                if (shouldResubmitPendingSync(syncJob, linkedJob.get())) {
                    submitClusterSyncAfterCommit(syncJob.asyncJobId());
                    auditLogRepositoryPort.save(AuditLog.create(
                            "CLUSTER_SYNC_RESUBMITTED",
                            "CLUSTER",
                            clusterId.toString(),
                            actor,
                            requestId
                    ));
                    return syncJob.asyncJobId();
                }
                auditLogRepositoryPort.save(AuditLog.create(
                        "CLUSTER_SYNC_REUSED",
                        "CLUSTER",
                        clusterId.toString(),
                        actor,
                        requestId
                ));
                return syncJob.asyncJobId();
            }

            syncJob.markAbandoned(
                    Instant.now(),
                    linkedJob.map(job -> "Linked async job is " + job.status())
                            .orElse("Linked async job not found")
            );
            syncJobRepositoryPort.save(syncJob);
            auditLogRepositoryPort.save(AuditLog.create(
                    "CLUSTER_SYNC_STALE_JOB_CLEARED",
                    "CLUSTER",
                    clusterId.toString(),
                    actor,
                    requestId
            ));
        }

        AsyncJob job = AsyncJob.pending(AsyncJobType.CLUSTER_SYNC);
        AsyncJob savedJob = asyncJobRepositoryPort.save(job);
        syncJobRepositoryPort.save(SyncJob.pending(savedJob.id(), clusterId, SyncType.MANUAL_CLUSTER, actor));
        auditLogRepositoryPort.save(AuditLog.create(
                "CLUSTER_SYNC_REQUESTED",
                "CLUSTER",
                clusterId.toString(),
                actor,
                requestId
        ));
        submitClusterSyncAfterCommit(savedJob.id());
        return savedJob.id();
    }

    /** JobApplicationService의 isReusableSyncJob 처리 조건의 충족 여부를 판단한다. */
    private boolean isReusableSyncJob(AsyncJob asyncJob) {
        return asyncJob.status() == AsyncJobStatus.PENDING || asyncJob.status() == AsyncJobStatus.RUNNING;
    }

    /** JobApplicationService의 shouldResubmitPendingSync 처리 조건의 충족 여부를 판단한다. */
    private boolean shouldResubmitPendingSync(SyncJob syncJob, AsyncJob asyncJob) {
        return syncJob.status() == SyncJobStatus.PENDING
                && asyncJob.status() == AsyncJobStatus.PENDING
                && syncJob.createdAt().isBefore(Instant.now().minus(PENDING_SYNC_RESUBMIT_THRESHOLD));
    }

    /** JobApplicationService의 submitClusterSyncAfterCommit 처리에 필요한 업무 로직을 수행한다. */
    private void submitClusterSyncAfterCommit(UUID jobId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            clusterSyncExecutorPort.submitClusterSync(jobId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /** 익명 구현체의 afterCommit 처리에 필요한 업무 로직을 수행한다. */
            @Override
            public void afterCommit() {
                clusterSyncExecutorPort.submitClusterSync(jobId);
            }
        });
    }
}
