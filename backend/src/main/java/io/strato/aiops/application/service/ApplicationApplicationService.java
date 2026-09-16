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

    /** ApplicationApplicationService 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
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

    /** ApplicationApplicationService의 deployDocker 처리에 필요한 업무 로직을 수행한다. */
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

    /** ApplicationApplicationService의 deployHelm 처리에 필요한 업무 로직을 수행한다. */
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

    /** ApplicationApplicationService의 listApplications 처리 결과를 조회해 반환한다. */
    @Override
    @Transactional(readOnly = true)
    public List<ManagedApplication> listApplications() {
        return applicationRepositoryPort.findRecent(RECENT_APPLICATION_LIMIT);
    }

    /** ApplicationApplicationService의 getApplication 처리 결과를 조회해 반환한다. */
    @Override
    @Transactional(readOnly = true)
    public ManagedApplication getApplication(UUID applicationId) {
        return requireApplication(applicationId);
    }

    /** ApplicationApplicationService의 getApplicationStatus 처리 결과를 조회해 반환한다. */
    @Override
    @Transactional(readOnly = true)
    public ApplicationStatusResult getApplicationStatus(UUID applicationId) {
        ManagedApplication application = requireApplication(applicationId);
        return new ApplicationStatusResult(application.id(), application.clusterId(), application.namespace(), application.name(),
                application.status(), application.lastSyncedAt(), application.lastSyncStatus(), application.lastSyncError());
    }

    /** ApplicationApplicationService의 startApplicationSync 처리에 필요한 업무 로직을 수행한다. */
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

    /** ApplicationApplicationService의 requestRestart 처리에 필요한 업무 로직을 수행한다. */
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

    /** ApplicationApplicationService의 previewRollback 처리에 필요한 업무 로직을 수행한다. */
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

    /** ApplicationApplicationService의 requestRollback 처리에 필요한 업무 로직을 수행한다. */
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

    /** ApplicationApplicationService의 toRollbackRevision 처리 데이터를 필요한 표현으로 변환한다. */
    private ApplicationRollbackRevisionResult toRollbackRevision(KubernetesDeploymentRevision revision) {
        return new ApplicationRollbackRevisionResult(revision.revision(), revision.current(), revision.replicaSetName(),
                revision.replicas(), revision.image(), revision.state(), revision.createdAt());
    }

    /** ApplicationApplicationService의 resolveApplicationStatus 처리에 필요한 결과를 조합해 반환한다. */
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

    /** ApplicationApplicationService의 deploymentApplicationStatus 처리에 필요한 업무 로직을 수행한다. */
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

    /** ApplicationApplicationService의 parseReplicaState 처리 데이터를 필요한 표현으로 변환한다. */
    private ReplicaState parseReplicaState(String status) {
        if (status == null || !status.contains("/")) {
            return new ReplicaState(0, 0);
        }
        String[] parts = status.split("/", 2);
        return new ReplicaState(parseInt(parts[0]), parseInt(parts[1]));
    }

    /** ApplicationApplicationService의 parseInt 처리 데이터를 필요한 표현으로 변환한다. */
    private int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    /** ApplicationApplicationService의 requireCluster 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void requireCluster(UUID clusterId) {
        clusterRepositoryPort.findById(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster not found: " + clusterId));
    }

    /** ApplicationApplicationService의 requireApplication 처리 입력과 현재 상태의 유효성을 검증한다. */
    private ManagedApplication requireApplication(UUID applicationId) {
        return applicationRepositoryPort.findById(applicationId)
                .orElseThrow(() -> new NoSuchElementException("Application not found: " + applicationId));
    }

    /** ApplicationApplicationService의 createJob 처리에 필요한 데이터를 생성하거나 저장한다. */
    private UUID createJob(AsyncJobType type) {
        AsyncJob job = asyncJobRepositoryPort.save(AsyncJob.pending(type));
        return job.id();
    }

    /** ApplicationApplicationService의 markJobSucceeded 처리에 필요한 업무 로직을 수행한다. */
    private void markJobSucceeded(UUID jobId) {
        AsyncJob job = asyncJobRepositoryPort.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Job not found: " + jobId));
        Instant now = Instant.now();
        job.markRunning(now);
        job.markSucceeded(now);
        asyncJobRepositoryPort.save(job);
    }

    /** ApplicationApplicationService의 markJobFailed 처리에 필요한 업무 로직을 수행한다. */
    private void markJobFailed(UUID jobId, String errorCode, String errorMessage) {
        AsyncJob job = asyncJobRepositoryPort.findById(jobId)
                .orElseThrow(() -> new NoSuchElementException("Job not found: " + jobId));
        Instant now = Instant.now();
        job.markRunning(now);
        job.markFailed(now, errorCode, errorMessage);
        asyncJobRepositoryPort.save(job);
    }

    /** ApplicationApplicationService의 credential 처리에 필요한 업무 로직을 수행한다. */
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

    /** ApplicationApplicationService의 message 처리에 필요한 업무 로직을 수행한다. */
    private String message(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    /** ApplicationApplicationService의 valueOrBlank 처리에 필요한 업무 로직을 수행한다. */
    private String valueOrBlank(String value) {
        return value == null ? "" : value;
    }

    /** ApplicationApplicationService의 audit 처리에 필요한 업무 로직을 수행한다. */
    private void audit(String action, UUID applicationId, String actor, String requestId) {
        auditLogRepositoryPort.save(AuditLog.create(action, "APPLICATION", applicationId.toString(), actor, requestId));
    }

    private record ReplicaState(int available, int desired) {
    }
}
