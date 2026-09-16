package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.AsyncJobRepositoryPort;
import io.strato.aiops.application.port.out.ClusterCredentialRepositoryPort;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesEventSnapshotRepositoryPort;
import io.strato.aiops.application.port.out.KubernetesResourceSnapshotRepositoryPort;
import io.strato.aiops.application.port.out.KubernetesStateInventory;
import io.strato.aiops.application.port.out.KubernetesStateSyncPort;
import io.strato.aiops.application.port.out.SecretCryptoPort;
import io.strato.aiops.application.port.out.SyncJobRepositoryPort;
import io.strato.aiops.domain.cluster.EncryptedClusterCredential;
import io.strato.aiops.domain.cluster.EncryptedSecret;
import io.strato.aiops.domain.job.AsyncJob;
import io.strato.aiops.domain.job.AsyncJobStatus;
import io.strato.aiops.domain.sync.KubernetesEventSnapshot;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;
import io.strato.aiops.domain.sync.SyncJob;
import io.strato.aiops.domain.sync.SyncJobStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ClusterSyncWorker {

    private final AsyncJobRepositoryPort asyncJobRepositoryPort;
    private final SyncJobRepositoryPort syncJobRepositoryPort;
    private final ClusterCredentialRepositoryPort clusterCredentialRepositoryPort;
    private final SecretCryptoPort secretCryptoPort;
    private final KubernetesStateSyncPort kubernetesStateSyncPort;
    private final KubernetesResourceSnapshotRepositoryPort resourceSnapshotRepositoryPort;
    private final KubernetesEventSnapshotRepositoryPort eventSnapshotRepositoryPort;

    /** ClusterSyncWorker 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public ClusterSyncWorker(
            AsyncJobRepositoryPort asyncJobRepositoryPort,
            SyncJobRepositoryPort syncJobRepositoryPort,
            ClusterCredentialRepositoryPort clusterCredentialRepositoryPort,
            SecretCryptoPort secretCryptoPort,
            KubernetesStateSyncPort kubernetesStateSyncPort,
            KubernetesResourceSnapshotRepositoryPort resourceSnapshotRepositoryPort,
            KubernetesEventSnapshotRepositoryPort eventSnapshotRepositoryPort
    ) {
        this.asyncJobRepositoryPort = asyncJobRepositoryPort;
        this.syncJobRepositoryPort = syncJobRepositoryPort;
        this.clusterCredentialRepositoryPort = clusterCredentialRepositoryPort;
        this.secretCryptoPort = secretCryptoPort;
        this.kubernetesStateSyncPort = kubernetesStateSyncPort;
        this.resourceSnapshotRepositoryPort = resourceSnapshotRepositoryPort;
        this.eventSnapshotRepositoryPort = eventSnapshotRepositoryPort;
    }

    /** ClusterSyncWorker의 runClusterSync 처리의 핵심 작업 흐름을 실행한다. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void runClusterSync(UUID asyncJobId) {
        AsyncJob asyncJob = asyncJobRepositoryPort.findById(asyncJobId)
                .orElseThrow(() -> new NoSuchElementException("Job not found: " + asyncJobId));
        SyncJob syncJob = syncJobRepositoryPort.findByAsyncJobId(asyncJobId)
                .orElseThrow(() -> new NoSuchElementException("Sync job not found: " + asyncJobId));
        if (asyncJob.status() != AsyncJobStatus.PENDING) {
            return;
        }

        try {
            Instant startedAt = Instant.now();
            asyncJob.markRunning(startedAt);
            syncJob.markRunning(startedAt);
            asyncJobRepositoryPort.save(asyncJob);
            syncJobRepositoryPort.save(syncJob);

            EncryptedClusterCredential credential = clusterCredentialRepositoryPort.findByClusterId(syncJob.clusterId())
                    .orElseThrow(() -> new NoSuchElementException("Cluster credential not found: " + syncJob.clusterId()));
            String payload = secretCryptoPort.decrypt(new EncryptedSecret(
                    credential.encryptedPayload(),
                    credential.keyId(),
                    credential.algorithm(),
                    credential.nonce()
            ));

            KubernetesStateInventory inventory = kubernetesStateSyncPort.collectClusterInventory(new KubernetesConnectionCredential(
                    credential.credentialType(),
                    payload
            ));

            List<KubernetesResourceSnapshot> resourceSnapshots = inventory.resources().stream()
                    .map(resource -> KubernetesResourceSnapshot.collected(syncJob.clusterId(), syncJob.id(), resource))
                    .toList();
            List<KubernetesEventSnapshot> eventSnapshots = inventory.events().stream()
                    .map(event -> KubernetesEventSnapshot.collected(syncJob.clusterId(), syncJob.id(), event))
                    .toList();

            resourceSnapshotRepositoryPort.saveAll(resourceSnapshots);
            eventSnapshotRepositoryPort.saveAll(eventSnapshots);

            Instant completedAt = Instant.now();
            if (isJobCanceled(asyncJobId)) {
                if (syncJob.status() == SyncJobStatus.RUNNING) {
                    syncJob.markFailed(completedAt, "Cluster sync job was canceled");
                    syncJobRepositoryPort.save(syncJob);
                }
                return;
            }
            asyncJob.markSucceeded(completedAt);
            syncJob.markSucceeded(completedAt, resourceSnapshots.size(), eventSnapshots.size());
            asyncJobRepositoryPort.save(asyncJob);
            syncJobRepositoryPort.save(syncJob);
        } catch (Exception exception) {
            markFailed(asyncJob, syncJob, exception);
        }
    }

    /** ClusterSyncWorker의 isJobCanceled 처리 조건의 충족 여부를 판단한다. */
    private boolean isJobCanceled(UUID asyncJobId) {
        return asyncJobRepositoryPort.findById(asyncJobId)
                .map(job -> job.status() == AsyncJobStatus.CANCELED)
                .orElse(false);
    }

    /** ClusterSyncWorker의 markFailed 처리에 필요한 업무 로직을 수행한다. */
    private void markFailed(AsyncJob asyncJob, SyncJob syncJob, Exception exception) {
        Instant failedAt = Instant.now();
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        if (asyncJob.status() == AsyncJobStatus.RUNNING) {
            asyncJob.markFailed(failedAt, "CLUSTER_SYNC_FAILED", message);
            asyncJobRepositoryPort.save(asyncJob);
        }
        if (syncJob.status() == SyncJobStatus.RUNNING) {
            syncJob.markFailed(failedAt, message);
            syncJobRepositoryPort.save(syncJob);
        }
    }
}
