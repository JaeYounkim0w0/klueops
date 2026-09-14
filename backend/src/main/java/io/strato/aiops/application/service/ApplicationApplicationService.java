package io.strato.aiops.application.service;

import io.strato.aiops.application.port.in.ApplicationDeploymentResult;
import io.strato.aiops.application.port.in.ApplicationRollbackPreviewResult;
import io.strato.aiops.application.port.in.ApplicationRollbackRevisionResult;
import io.strato.aiops.application.port.in.ApplicationStatusResult;
import io.strato.aiops.application.port.in.ApplicationUseCase;
import io.strato.aiops.application.port.in.DeployDockerApplicationCommand;
import io.strato.aiops.application.port.in.DeployHelmApplicationCommand;
import io.strato.aiops.application.port.out.AsyncJobRepositoryPort;
import io.strato.aiops.application.port.out.AuditLogRepositoryPort;
import io.strato.aiops.application.port.out.ClusterCredentialRepositoryPort;
import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesDeploymentRevision;
import io.strato.aiops.application.port.out.KubernetesMutationPort;
import io.strato.aiops.application.port.out.KubernetesMutationResult;
import io.strato.aiops.application.port.out.KubernetesResourceSnapshotRepositoryPort;
import io.strato.aiops.application.port.out.KubernetesRollbackPlan;
import io.strato.aiops.application.port.out.ManagedApplicationRepositoryPort;
import io.strato.aiops.application.port.out.SecretCryptoPort;
import io.strato.aiops.domain.application.ApplicationStatus;
import io.strato.aiops.domain.application.ManagedApplication;
import io.strato.aiops.domain.audit.AuditLog;
import io.strato.aiops.domain.cluster.EncryptedClusterCredential;
import io.strato.aiops.domain.cluster.EncryptedSecret;
import io.strato.aiops.domain.job.AsyncJob;
import io.strato.aiops.domain.job.AsyncJobType;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ApplicationApplicationService implements ApplicationUseCase {

    private static final int RECENT_APPLICATION_LIMIT = 100;

    private final ClusterRepositoryPort clusterRepositoryPort;
    private final ManagedApplicationRepositoryPort applicationRepositoryPort;
    private final AsyncJobRepositoryPort asyncJobRepositoryPort;
    private final AuditLogRepositoryPort auditLogRepositoryPort;
    private final ClusterCredentialRepositoryPort clusterCredentialRepositoryPort;
    private final SecretCryptoPort secretCryptoPort;
    private final KubernetesMutationPort kubernetesMutationPort;
    private final KubernetesResourceSnapshotRepositoryPort resourceSnapshotRepositoryPort;

    public ApplicationApplicationService(ClusterRepositoryPort clusterRepositoryPort,
                                         ManagedApplicationRepositoryPort applicationRepositoryPort,
                                         AsyncJobRepositoryPort asyncJobRepositoryPort,
                                         AuditLogRepositoryPort auditLogRepositoryPort,
                                         ClusterCredentialRepositoryPort clusterCredentialRepositoryPort,
                                         SecretCryptoPort secretCryptoPort,
                                         KubernetesMutationPort kubernetesMutationPort,
                                         KubernetesResourceSnapshotRepositoryPort resourceSnapshotRepositoryPort) {
        this.clusterRepositoryPort = clusterRepositoryPort;
        this.applicationRepositoryPort = applicationRepositoryPort;
        this.asyncJobRepositoryPort = asyncJobRepositoryPort;
        this.auditLogRepositoryPort = auditLogRepositoryPort;
        this.clusterCredentialRepositoryPort = clusterCredentialRepositoryPort;
        this.secretCryptoPort = secretCryptoPort;
        this.kubernetesMutationPort = kubernetesMutationPort;
        this.resourceSnapshotRepositoryPort = resourceSnapshotRepositoryPort;
    }

    @Override
    @Transactional
    public ApplicationDeploymentResult deployDocker(DeployDockerApplicationCommand command, String actor, String requestId) {
        requireCluster(command.clusterId());
        ManagedApplication application = applicationRepositoryPort.save(ManagedApplication.dockerImage(
                command.clusterId(), command.namespace(), command.name(), command.image(), actor));
        UUID jobId = createJob(AsyncJobType.APPLICATION_DEPLOY);
        audit("APPLICATION_DOCKER_DEPLOY_REQUESTED", application.id(), actor, requestId);
        return new ApplicationDeploymentResult(application, jobId);
    }

    @Override
    @Transactional
    public ApplicationDeploymentResult deployHelm(DeployHelmApplicationCommand command, String actor, String requestId) {
        requireCluster(command.clusterId());
        ManagedApplication application = applicationRepositoryPort.save(ManagedApplication.helmChart(
                command.clusterId(), command.namespace(), command.name(), command.releaseName(), command.chart(), actor));
        UUID jobId = createJob(AsyncJobType.APPLICATION_DEPLOY);
        audit("APPLICATION_HELM_DEPLOY_REQUESTED", application.id(), actor, requestId);
        return new ApplicationDeploymentResult(application, jobId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManagedApplication> listApplications() {
        return applicationRepositoryPort.findRecent(RECENT_APPLICATION_LIMIT);
    }

    @Override
    @Transactional(readOnly = true)
    public ManagedApplication getApplication(UUID applicationId) {
        return requireApplication(applicationId);
    }

    @Override
    @Transactional(readOnly = true)
    public ApplicationStatusResult getApplicationStatus(UUID applicationId) {
        ManagedApplication application = requireApplication(applicationId);
        return new ApplicationStatusResult(application.id(), application.clusterId(), application.namespace(), application.name(),
                application.status(), application.lastSyncedAt(), application.lastSyncStatus(), application.lastSyncError());
    }

    @Override
    @Transactional
    public UUID startApplicationSync(UUID applicationId, String actor, String requestId) {
        ManagedApplication application = requireApplication(applicationId);
        UUID jobId = createJob(AsyncJobType.APPLICATION_SYNC);
        applicationRepositoryPort.save(resolveApplicationStatus(application, Instant.now()));
        markJobSucceeded(jobId);
        audit("APPLICATION_SYNC_REQUESTED", application.id(), actor, requestId);
        return jobId;
    }

    @Override
    @Transactional
    public UUID requestRestart(UUID applicationId, String actor, String requestId) {
        ManagedApplication application = requireApplication(applicationId);
        UUID jobId = createJob(AsyncJobType.APPLICATION_RESTART);
        try {
            kubernetesMutationPort.dryRunRolloutRestartDeployment(
                    credential(application.clusterId()),
                    application.namespace(),
                    application.name()
            );
            KubernetesMutationResult result = kubernetesMutationPort.rolloutRestartDeployment(
                    credential(application.clusterId()),
                    application.namespace(),
                    application.name()
            );
            markJobSucceeded(jobId);
            audit("APPLICATION_RESTART_EXECUTED", application.id(), actor, requestId);
        } catch (RuntimeException exception) {
            markJobFailed(jobId, "APPLICATION_RESTART_FAILED", message(exception));
            audit("APPLICATION_RESTART_FAILED", application.id(), actor, requestId);
        }
        return jobId;
    }

    @Override
    @Transactional(readOnly = true)
    public ApplicationRollbackPreviewResult previewRollback(UUID applicationId, Integer targetRevision) {
        ManagedApplication application = requireApplication(applicationId);
        KubernetesConnectionCredential credential = credential(application.clusterId());
        List<ApplicationRollbackRevisionResult> revisions = kubernetesMutationPort
                .listDeploymentRevisions(credential, application.namespace(), application.name())
                .stream()
                .map(this::toRollbackRevision)
                .toList();
        KubernetesRollbackPlan plan = kubernetesMutationPort.previewRollbackDeployment(
                credential,
                application.namespace(),
                application.name(),
                targetRevision
        );
        return new ApplicationRollbackPreviewResult(application.id(), application.clusterId(), application.namespace(),
                application.name(), plan.currentRevision(), plan.targetRevision(), plan.executable(), plan.reason(),
                plan.confirmationText(), plan.currentState(), plan.targetState(), revisions, plan.plannedAt());
    }

    @Override
    @Transactional
    public UUID requestRollback(UUID applicationId, Integer targetRevision, String confirmText, String actor, String requestId) {
        ManagedApplication application = requireApplication(applicationId);
        UUID jobId = createJob(AsyncJobType.APPLICATION_ROLLBACK);
        try {
            if (targetRevision == null || targetRevision <= 0) {
                throw new IllegalArgumentException("Rollback targetRevision is required.");
            }
            KubernetesRollbackPlan plan = kubernetesMutationPort.previewRollbackDeployment(
                    credential(application.clusterId()),
                    application.namespace(),
                    application.name(),
                    targetRevision
            );
            if (!plan.executable()) {
                throw new IllegalStateException(plan.reason());
            }
            if (!plan.confirmationText().equals(valueOrBlank(confirmText).trim())) {
                throw new IllegalArgumentException("Rollback confirmation text does not match.");
            }
            KubernetesMutationResult result = kubernetesMutationPort.rollbackDeployment(
                    credential(application.clusterId()),
                    application.namespace(),
                    application.name(),
                    targetRevision
            );
            markJobSucceeded(jobId);
            audit("APPLICATION_ROLLBACK_EXECUTED", application.id(), actor, requestId);
        } catch (RuntimeException exception) {
            markJobFailed(jobId, "APPLICATION_ROLLBACK_GUARDED", message(exception));
            audit("APPLICATION_ROLLBACK_BLOCKED", application.id(), actor, requestId);
        }
        return jobId;
    }

    private ApplicationRollbackRevisionResult toRollbackRevision(KubernetesDeploymentRevision revision) {
        return new ApplicationRollbackRevisionResult(revision.revision(), revision.current(), revision.replicaSetName(),
                revision.replicas(), revision.image(), revision.state(), revision.createdAt());
    }

    private ManagedApplication resolveApplicationStatus(ManagedApplication application, Instant syncedAt) {
        return resourceSnapshotRepositoryPort
                .findLatest(application.clusterId(), application.namespace(), "Deployment", RECENT_APPLICATION_LIMIT)
                .stream()
                .filter(snapshot -> application.name().equals(snapshot.resourceName()))
                .findFirst()
                .map(snapshot -> application.synced(
                        deploymentApplicationStatus(snapshot),
                        syncedAt,
                        "SNAPSHOT_SYNCED",
                        snapshot.status()
                ))
                .orElseGet(() -> application.synced(
                        ApplicationStatus.UNKNOWN,
                        syncedAt,
                        "SNAPSHOT_NOT_FOUND",
                        "Deployment snapshot not found. Run cluster sync before application sync."
                ));
    }

    private ApplicationStatus deploymentApplicationStatus(KubernetesResourceSnapshot snapshot) {
        ReplicaState replicas = parseReplicaState(snapshot.status());
        if (replicas.desired() > 0 && replicas.available() >= replicas.desired()) {
            return ApplicationStatus.RUNNING;
        }
        if (replicas.desired() > 0) {
            return ApplicationStatus.DEGRADED;
        }
        return ApplicationStatus.UNKNOWN;
    }

    private ReplicaState parseReplicaState(String status) {
        if (status == null || !status.contains("/")) {
            return new ReplicaState(0, 0);
        }
        String[] parts = status.split("/", 2);
        return new ReplicaState(parseInt(parts[0]), parseInt(parts[1]));
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private void requireCluster(UUID clusterId) {
        clusterRepositoryPort.findById(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster not found: " + clusterId));
    }

    private ManagedApplication requireApplication(UUID applicationId) {
        return applicationRepositoryPort.findById(applicationId)
                .orElseThrow(() -> new NoSuchElementException("Application not found: " + applicationId));
    }

    private UUID createJob(AsyncJobType type) {
        AsyncJob job = asyncJobRepositoryPort.save(AsyncJob.pending(type));
        return job.id();
    }

    private void markJobSucceeded(UUID jobId) {
        AsyncJob job = asyncJobRepositoryPort.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Job not found: " + jobId));
        Instant now = Instant.now();
        job.markRunning(now);
        job.markSucceeded(now);
        asyncJobRepositoryPort.save(job);
    }

    private void markJobFailed(UUID jobId, String errorCode, String errorMessage) {
        AsyncJob job = asyncJobRepositoryPort.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Job not found: " + jobId));
        Instant now = Instant.now();
        job.markRunning(now);
        job.markFailed(now, errorCode, errorMessage);
        asyncJobRepositoryPort.save(job);
    }

    private KubernetesConnectionCredential credential(UUID clusterId) {
        EncryptedClusterCredential credential = clusterCredentialRepositoryPort.findByClusterId(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster credential not found: " + clusterId));
        String payload = secretCryptoPort.decrypt(new EncryptedSecret(
                credential.encryptedPayload(),
                credential.keyId(),
                credential.algorithm(),
                credential.nonce()
        ));
        return new KubernetesConnectionCredential(credential.credentialType(), payload);
    }

    private String message(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private String valueOrBlank(String value) {
        return value == null ? "" : value;
    }

    private void audit(String action, UUID applicationId, String actor, String requestId) {
        auditLogRepositoryPort.save(AuditLog.create(action, "APPLICATION", applicationId.toString(), actor, requestId));
    }

    private record ReplicaState(int available, int desired) {
    }
}
