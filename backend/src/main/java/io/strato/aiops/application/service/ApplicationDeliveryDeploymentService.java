package io.strato.aiops.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.ApplicationDeliveryRepositoryPort;
import io.strato.aiops.application.port.out.ApplicationDeploymentExecutorPort;
import io.strato.aiops.application.port.out.ApplicationLifecycleRepositoryPort;
import io.strato.aiops.application.port.out.AsyncJobRepositoryPort;
import io.strato.aiops.application.port.out.ClusterCredentialRepositoryPort;
import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.application.port.out.HelmDeploymentPort;
import io.strato.aiops.application.port.out.ManagedApplicationRepositoryPort;
import io.strato.aiops.application.port.out.RenderedManifestSanitizationPort;
import io.strato.aiops.application.port.out.SecretCryptoPort;
import io.strato.aiops.domain.application.ApplicationStatus;
import io.strato.aiops.domain.application.ManagedApplication;
import io.strato.aiops.domain.applicationdelivery.ApplicationRelease;
import io.strato.aiops.domain.applicationdelivery.DeploymentPlan;
import io.strato.aiops.domain.applicationdelivery.ReleaseOperation;
import io.strato.aiops.domain.cluster.EncryptedClusterCredential;
import io.strato.aiops.domain.cluster.EncryptedSecret;
import io.strato.aiops.domain.job.AsyncJob;
import io.strato.aiops.domain.job.AsyncJobType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ApplicationDeliveryDeploymentService {
    private static final Pattern DNS_LABEL = Pattern.compile("[a-z0-9]([-a-z0-9]*[a-z0-9])?");
    private static final Pattern HOSTNAME = Pattern.compile("[a-z0-9]([-a-z0-9.]*[a-z0-9])?");
    private final ApplicationDeliveryRepositoryPort catalog;
    private final ApplicationLifecycleRepositoryPort lifecycle;
    private final ClusterRepositoryPort clusters;
    private final ClusterCredentialRepositoryPort credentials;
    private final SecretCryptoPort crypto;
    private final HelmDeploymentPort helm;
    private final ManagedApplicationRepositoryPort applications;
    private final AsyncJobRepositoryPort jobs;
    private final ApplicationDeploymentExecutorPort executor;
    private final RenderedManifestSanitizationPort manifestSanitizer;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ApplicationDeliveryDeploymentService(ApplicationDeliveryRepositoryPort catalog,
                                                ApplicationLifecycleRepositoryPort lifecycle,
                                                ClusterRepositoryPort clusters,
                                                ClusterCredentialRepositoryPort credentials,
                                                SecretCryptoPort crypto, HelmDeploymentPort helm,
                                                ManagedApplicationRepositoryPort applications,
                                                AsyncJobRepositoryPort jobs,
                                                ApplicationDeploymentExecutorPort executor,
                                                RenderedManifestSanitizationPort manifestSanitizer,
                                                ObjectMapper objectMapper, Clock clock) {
        this.catalog = catalog;
        this.lifecycle = lifecycle;
        this.clusters = clusters;
        this.credentials = credentials;
        this.crypto = crypto;
        this.helm = helm;
        this.applications = applications;
        this.jobs = jobs;
        this.executor = executor;
        this.manifestSanitizer = manifestSanitizer;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public DeploymentPlan preview(UUID tenantId, UUID clusterId, UUID chartVersionId, UUID valuesRevisionId,
                                  String namespace, String releaseName, String exposureType, String hostname,
                                  String actor) {
        var cluster = clusters.findById(clusterId).orElseThrow();
        if (!cluster.tenantId().equals(tenantId)) throw new NoSuchElementException("Cluster not found");
        validateTarget(namespace, releaseName, exposureType, hostname);
        var version = catalog.findVersion(tenantId, chartVersionId).orElseThrow();
        String values = valuesRevisionId == null ? null : decryptedValues(tenantId, valuesRevisionId);
        String manifest = render(releaseName, namespace, catalog.loadArtifact(tenantId, chartVersionId), values);
        List<String> warnings = scanRisks(manifest);
        Instant now = clock.instant();
        DeploymentPlan plan = new DeploymentPlan(UUID.randomUUID(), clusterId, chartVersionId, valuesRevisionId,
                namespace, releaseName, exposureType == null ? "NONE" : exposureType, hostname,
                manifestSanitizer.sanitize(manifest),
                sha256(manifest.getBytes(StandardCharsets.UTF_8)), json(warnings),
                "DEPLOY " + releaseName + " TO " + cluster.name() + "/" + namespace,
                actor, now, now.plus(Duration.ofMinutes(15)), null);
        return lifecycle.savePlan(plan);
    }

    @Transactional
    public DeploymentAccepted deploy(UUID tenantId, UUID planId, String confirmation, String actor) {
        DeploymentPlan plan = lifecycle.findPlan(tenantId, planId).orElseThrow();
        if (!plan.executableAt(clock.instant())) throw new IllegalStateException("Deployment plan expired or was already used");
        if (!plan.confirmationText().equals(confirmation == null ? "" : confirmation.trim())) {
            throw new IllegalArgumentException("Deployment confirmation text does not match");
        }
        if (!lifecycle.consumePlan(plan.id(), clock.instant())) throw new IllegalStateException("Deployment plan is no longer executable");
        ManagedApplication application = applications.save(ManagedApplication.helmChart(plan.clusterId(), plan.namespace(),
                plan.releaseName(), plan.releaseName(), plan.chartVersionId().toString(), actor)
                .withReleaseMetadata(null, plan.chartVersionId(), plan.valuesRevisionId())
                .withStatus(ApplicationStatus.DEPLOYING, "HELM_INSTALL_QUEUED", null));
        AsyncJob job = jobs.save(AsyncJob.pending(AsyncJobType.HELM_INSTALL));
        ReleaseOperation operation = lifecycle.saveOperation(new ReleaseOperation(UUID.randomUUID(), application.id(),
                job.id(), "INSTALL", "PENDING", null, null, null, actor, clock.instant(), null));
        try {
            executor.submit(plan.id(), tenantId, application.id(), job.id(), operation.id());
        } catch (RuntimeException exception) {
            job.markSubmissionFailed(clock.instant(), exception.getMessage());
            jobs.save(job);
            applications.save(application.withStatus(ApplicationStatus.FAILED, "HELM_INSTALL_REJECTED", safeMessage(exception)));
            lifecycle.saveOperation(new ReleaseOperation(operation.id(), application.id(), job.id(), "INSTALL", "FAILED",
                    null, null, safeMessage(exception), actor, operation.requestedAt(), clock.instant()));
            throw exception;
        }
        return new DeploymentAccepted(application.id(), job.id(), operation.id());
    }

    public void executeInstall(UUID planId, UUID tenantId, UUID applicationId, UUID jobId, UUID operationId) {
        AsyncJob job = jobs.findById(jobId).orElseThrow();
        ManagedApplication application = applications.findById(applicationId).orElseThrow();
        DeploymentPlan plan = lifecycle.findPlan(tenantId, planId).orElseThrow();
        Instant started = clock.instant();
        job.markRunning(started);
        jobs.save(job);
        lifecycle.saveOperation(new ReleaseOperation(operationId, applicationId, jobId, "INSTALL", "RUNNING",
                null, null, null, plan.createdBy(), started, null));
        try {
            executeHelmInstall(plan, tenantId);
            Instant completed = clock.instant();
            lifecycle.saveRelease(new ApplicationRelease(UUID.randomUUID(), applicationId, 1, plan.chartVersionId(),
                    plan.valuesRevisionId(), plan.manifestSha256(), "DEPLOYED", plan.createdBy(), completed));
            applications.save(application.withReleaseMetadata(1, plan.chartVersionId(), plan.valuesRevisionId())
                    .withStatus(ApplicationStatus.RUNNING, "HELM_INSTALL_SUCCEEDED", null));
            job.markSucceeded(completed);
            jobs.save(job);
            lifecycle.saveOperation(new ReleaseOperation(operationId, applicationId, jobId, "INSTALL", "SUCCEEDED",
                    1, "Helm install completed", null, plan.createdBy(), started, completed));
        } catch (RuntimeException exception) {
            Instant completed = clock.instant();
            applications.save(application.withStatus(ApplicationStatus.FAILED, "HELM_INSTALL_FAILED", safeMessage(exception)));
            job.markFailed(completed, "HELM_INSTALL_FAILED", safeMessage(exception));
            jobs.save(job);
            lifecycle.saveOperation(new ReleaseOperation(operationId, applicationId, jobId, "INSTALL", "FAILED",
                    null, null, safeMessage(exception), plan.createdBy(), started, completed));
        }
    }

    @Transactional(readOnly = true)
    public List<ManagedApplication> applications(UUID tenantId) {
        List<UUID> clusterIds = clusters.findAll(tenantId, null).stream().map(cluster -> cluster.id()).toList();
        return applications.findRecentByClusterIds(clusterIds, 200);
    }

    @Transactional(readOnly = true)
    public List<ReleaseOperation> operations(UUID tenantId, UUID applicationId) {
        return lifecycle.findOperations(tenantId, applicationId, 100);
    }

    @Transactional(readOnly = true)
    public List<ApplicationRelease> releases(UUID tenantId, UUID applicationId) {
        return lifecycle.findReleases(tenantId, applicationId, 100);
    }

    @Transactional(readOnly = true)
    public LifecycleConfirmation rollbackConfirmation(UUID tenantId, UUID applicationId, int revision) {
        ManagedApplication application = requireApplication(tenantId, applicationId);
        if (revision < 1) throw new IllegalArgumentException("Rollback revision must be positive");
        return new LifecycleConfirmation("ROLLBACK " + application.helmReleaseName() + " TO REVISION " + revision,
                "Helm release를 선택한 revision으로 되돌립니다. PVC와 Namespace는 유지됩니다.");
    }

    @Transactional
    public DeploymentAccepted rollback(UUID tenantId, UUID applicationId, int revision, String confirmation, String actor) {
        ManagedApplication application = requireApplication(tenantId, applicationId);
        LifecycleConfirmation expected = rollbackConfirmation(tenantId, applicationId, revision);
        requireConfirmation(expected.confirmationText(), confirmation);
        return queueLifecycle(application, tenantId, AsyncJobType.HELM_ROLLBACK, "ROLLBACK", revision, actor);
    }

    @Transactional(readOnly = true)
    public LifecycleConfirmation uninstallConfirmation(UUID tenantId, UUID applicationId) {
        ManagedApplication application = requireApplication(tenantId, applicationId);
        return new LifecycleConfirmation("UNINSTALL " + application.helmReleaseName(),
                "Helm release를 제거합니다. 공유 Namespace와 Tenant Chart Library는 삭제하지 않습니다.");
    }

    @Transactional
    public DeploymentAccepted uninstall(UUID tenantId, UUID applicationId, String confirmation, String actor) {
        ManagedApplication application = requireApplication(tenantId, applicationId);
        LifecycleConfirmation expected = uninstallConfirmation(tenantId, applicationId);
        requireConfirmation(expected.confirmationText(), confirmation);
        return queueLifecycle(application, tenantId, AsyncJobType.HELM_UNINSTALL, "UNINSTALL", null, actor);
    }

    public void executeRollback(UUID tenantId, UUID applicationId, int revision, UUID jobId, UUID operationId,
                                String actor) {
        executeLifecycle(tenantId, applicationId, jobId, operationId, actor, "ROLLBACK", revision);
    }

    public void executeUninstall(UUID tenantId, UUID applicationId, UUID jobId, UUID operationId, String actor) {
        executeLifecycle(tenantId, applicationId, jobId, operationId, actor, "UNINSTALL", null);
    }

    private String render(String releaseName, String namespace, byte[] archive, String values) {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("klueops-preview-");
            Path chart = directory.resolve("chart.tgz");
            Files.write(chart, archive);
            List<String> arguments = new ArrayList<>(List.of("template", releaseName, chart.toString(),
                    "--namespace", namespace, "--include-crds"));
            if (values != null) {
                Path valuesFile = directory.resolve("values.yaml");
                Files.writeString(valuesFile, values, StandardCharsets.UTF_8);
                arguments.addAll(List.of("--values", valuesFile.toString()));
            }
            var result = helm.execute(arguments, Duration.ofSeconds(45));
            if (result.exitCode() != 0) throw new IllegalArgumentException("Helm template failed: " + bounded(result.stderr()));
            return result.stdout();
        } catch (IOException exception) {
            throw new IllegalStateException("Deployment preview workspace could not be created", exception);
        } finally {
            delete(directory);
        }
    }

    private void executeHelmInstall(DeploymentPlan plan, UUID tenantId) {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("klueops-deploy-");
            Path chart = directory.resolve("chart.tgz");
            Path kubeconfig = directory.resolve("kubeconfig");
            Files.write(chart, catalog.loadArtifact(tenantId, plan.chartVersionId()));
            Files.writeString(kubeconfig, kubeconfig(plan.clusterId()), StandardCharsets.UTF_8);
            List<String> arguments = new ArrayList<>(List.of("upgrade", "--install", plan.releaseName(), chart.toString(),
                    "--namespace", plan.namespace(), "--create-namespace", "--atomic", "--wait", "--timeout", "5m",
                    "--kubeconfig", kubeconfig.toString()));
            if (plan.valuesRevisionId() != null) {
                Path values = directory.resolve("values.yaml");
                Files.writeString(values, decryptedValues(tenantId, plan.valuesRevisionId()), StandardCharsets.UTF_8);
                arguments.addAll(List.of("--values", values.toString()));
            }
            var result = helm.execute(arguments, Duration.ofMinutes(6));
            if (result.exitCode() != 0) throw new IllegalStateException("Helm install failed: " + bounded(result.stderr()));
        } catch (IOException exception) {
            throw new IllegalStateException("Helm execution workspace could not be created", exception);
        } finally {
            delete(directory);
        }
    }

    private DeploymentAccepted queueLifecycle(ManagedApplication application, UUID tenantId, AsyncJobType jobType,
                                               String operationType, Integer revision, String actor) {
        ApplicationStatus queuedStatus = "ROLLBACK".equals(operationType)
                ? ApplicationStatus.ROLLING_BACK : ApplicationStatus.UNINSTALLING;
        applications.save(application.withStatus(queuedStatus, "HELM_" + operationType + "_QUEUED", null));
        AsyncJob job = jobs.save(AsyncJob.pending(jobType));
        ReleaseOperation operation = lifecycle.saveOperation(new ReleaseOperation(UUID.randomUUID(), application.id(),
                job.id(), operationType, "PENDING", revision, null, null, actor, clock.instant(), null));
        if ("ROLLBACK".equals(operationType)) {
            executor.submitRollback(tenantId, application.id(), revision, job.id(), operation.id(), actor);
        } else {
            executor.submitUninstall(tenantId, application.id(), job.id(), operation.id(), actor);
        }
        return new DeploymentAccepted(application.id(), job.id(), operation.id());
    }

    private void executeLifecycle(UUID tenantId, UUID applicationId, UUID jobId, UUID operationId, String actor,
                                  String operationType, Integer revision) {
        AsyncJob job = jobs.findById(jobId).orElseThrow();
        ManagedApplication application = requireApplication(tenantId, applicationId);
        Instant started = clock.instant();
        job.markRunning(started);
        jobs.save(job);
        lifecycle.saveOperation(new ReleaseOperation(operationId, applicationId, jobId, operationType, "RUNNING",
                revision, null, null, actor, started, null));
        try {
            executeHelmLifecycle(application, operationType, revision);
            Instant completed = clock.instant();
            ApplicationStatus status = "UNINSTALL".equals(operationType) ? ApplicationStatus.UNINSTALLED : ApplicationStatus.RUNNING;
            applications.save(application.withStatus(status, "HELM_" + operationType + "_SUCCEEDED", null));
            job.markSucceeded(completed);
            jobs.save(job);
            lifecycle.saveOperation(new ReleaseOperation(operationId, applicationId, jobId, operationType, "SUCCEEDED",
                    revision, "Helm " + operationType.toLowerCase() + " completed", null, actor, started, completed));
        } catch (RuntimeException exception) {
            Instant completed = clock.instant();
            applications.save(application.withStatus(ApplicationStatus.FAILED, "HELM_" + operationType + "_FAILED",
                    safeMessage(exception)));
            job.markFailed(completed, "HELM_" + operationType + "_FAILED", safeMessage(exception));
            jobs.save(job);
            lifecycle.saveOperation(new ReleaseOperation(operationId, applicationId, jobId, operationType, "FAILED",
                    revision, null, safeMessage(exception), actor, started, completed));
        }
    }

    private void executeHelmLifecycle(ManagedApplication application, String operationType, Integer revision) {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("klueops-lifecycle-");
            Path kubeconfig = directory.resolve("kubeconfig");
            Files.writeString(kubeconfig, kubeconfig(application.clusterId()), StandardCharsets.UTF_8);
            List<String> arguments = new ArrayList<>();
            if ("ROLLBACK".equals(operationType)) {
                arguments.addAll(List.of("rollback", application.helmReleaseName(), String.valueOf(revision),
                        "--namespace", application.namespace(), "--wait", "--timeout", "5m"));
            } else {
                arguments.addAll(List.of("uninstall", application.helmReleaseName(), "--namespace", application.namespace(),
                        "--wait", "--timeout", "5m"));
            }
            arguments.addAll(List.of("--kubeconfig", kubeconfig.toString()));
            var result = helm.execute(arguments, Duration.ofMinutes(6));
            if (result.exitCode() != 0) throw new IllegalStateException("Helm " + operationType.toLowerCase()
                    + " failed: " + bounded(result.stderr()));
        } catch (IOException exception) {
            throw new IllegalStateException("Helm lifecycle workspace could not be created", exception);
        } finally {
            delete(directory);
        }
    }

    private String kubeconfig(UUID clusterId) {
        EncryptedClusterCredential stored = credentials.findByClusterId(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster credential not found"));
        return crypto.decrypt(new EncryptedSecret(stored.encryptedPayload(), stored.keyId(), stored.algorithm(), stored.nonce()));
    }

    private String decryptedValues(UUID tenantId, UUID revisionId) {
        return crypto.decrypt(catalog.findRevision(tenantId, revisionId).orElseThrow().encryptedValues());
    }

    private ManagedApplication requireApplication(UUID tenantId, UUID applicationId) {
        ManagedApplication application = applications.findById(applicationId).orElseThrow();
        var cluster = clusters.findById(application.clusterId()).orElseThrow();
        if (!cluster.tenantId().equals(tenantId)) throw new NoSuchElementException("Application not found");
        return application;
    }

    private void requireConfirmation(String expected, String actual) {
        if (!expected.equals(actual == null ? "" : actual.trim()))
            throw new IllegalArgumentException("Lifecycle confirmation text does not match");
    }

    private void validateTarget(String namespace, String releaseName, String exposureType, String hostname) {
        if (namespace == null || namespace.length() > 63 || !DNS_LABEL.matcher(namespace).matches())
            throw new IllegalArgumentException("Namespace must be a valid DNS label");
        if (releaseName == null || releaseName.length() > 53 || !DNS_LABEL.matcher(releaseName).matches())
            throw new IllegalArgumentException("Release name must be a valid Helm release name");
        String exposure = exposureType == null ? "NONE" : exposureType;
        if (!List.of("NONE", "HTTP_ROUTE").contains(exposure)) throw new IllegalArgumentException("Unsupported exposure type");
        if ("HTTP_ROUTE".equals(exposure) && (hostname == null || hostname.length() > 253 || !HOSTNAME.matcher(hostname).matches()))
            throw new IllegalArgumentException("A valid hostname is required for HTTPRoute exposure");
    }

    private List<String> scanRisks(String manifest) {
        List<String> warnings = new ArrayList<>();
        if (manifest.contains("kind: CustomResourceDefinition")) warnings.add("CRD 포함: Cluster 범위 변경을 검토하세요.");
        if (manifest.contains("kind: ClusterRole") || manifest.contains("kind: ClusterRoleBinding")) warnings.add("Cluster RBAC 권한이 포함됩니다.");
        if (manifest.contains("privileged: true")) warnings.add("Privileged container가 포함됩니다.");
        if (manifest.contains("hostPath:")) warnings.add("hostPath volume이 포함됩니다.");
        if (manifest.contains("kind: ValidatingWebhookConfiguration") || manifest.contains("kind: MutatingWebhookConfiguration"))
            warnings.add("Admission webhook이 포함됩니다.");
        return warnings;
    }

    private String json(List<String> warnings) {
        try { return objectMapper.writeValueAsString(warnings); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Warnings serialization failed", exception); }
    }

    private String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    private String bounded(String value) {
        if (value == null) return "Unknown Helm error";
        return value.length() <= 1800 ? value : value.substring(0, 1800);
    }

    private String safeMessage(RuntimeException exception) { return bounded(exception.getMessage()); }

    private void delete(Path directory) {
        if (directory == null) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) {
            // 임시 파일 정리 실패가 실제 Helm 결과를 덮지 않도록 한다.
        }
    }

    public record DeploymentAccepted(UUID applicationId, UUID jobId, UUID operationId) { }
    public record LifecycleConfirmation(String confirmationText, String impactSummary) { }
}
