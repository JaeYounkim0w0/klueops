package io.strato.aiops.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.ApplicationDeliveryRepositoryPort;
import io.strato.aiops.application.port.out.ApplicationDeploymentExecutorPort;
import io.strato.aiops.application.port.out.ApplicationLifecycleRepositoryPort;
import io.strato.aiops.application.port.out.AsyncJobRepositoryPort;
import io.strato.aiops.application.port.out.ClusterCredentialRepositoryPort;
import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.application.port.out.ManagedApplicationRepositoryPort;
import io.strato.aiops.application.port.out.RenderedManifestSanitizationPort;
import io.strato.aiops.application.port.out.ApplicationRuntimeInspectionPort;
import io.strato.aiops.application.port.out.ApplicationExposurePort;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.SecretCryptoPort;
import io.strato.aiops.domain.application.ApplicationStatus;
import io.strato.aiops.domain.application.ManagedApplication;
import io.strato.aiops.domain.applicationdelivery.ApplicationRelease;
import io.strato.aiops.domain.applicationdelivery.ApplicationExposureMode;
import io.strato.aiops.domain.applicationdelivery.DeploymentPlan;
import io.strato.aiops.domain.applicationdelivery.ReleaseOperation;
import io.strato.aiops.domain.applicationdelivery.ApplicationEndpoint;
import io.strato.aiops.domain.cluster.EncryptedClusterCredential;
import io.strato.aiops.domain.cluster.EncryptedSecret;
import io.strato.aiops.domain.job.AsyncJob;
import io.strato.aiops.domain.job.AsyncJobType;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final HelmReleaseCommandRunner helmRunner;
    private final HelmCredentialKubeconfigFactory kubeconfigFactory;
    private final ManagedApplicationRepositoryPort applications;
    private final AsyncJobRepositoryPort jobs;
    private final ApplicationDeploymentExecutorPort executor;
    private final ApplicationOperationConcurrencyPolicy concurrencyPolicy;
    private final RenderedManifestSanitizationPort manifestSanitizer;
    private final ApplicationRuntimeInspectionPort runtimeInspection;
    private final ApplicationExposurePort exposure;
    private final HelmReleaseStoragePreflight helmStoragePreflight;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** ApplicationDeliveryDeploymentService 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public ApplicationDeliveryDeploymentService(ApplicationDeliveryRepositoryPort catalog,
                                                ApplicationLifecycleRepositoryPort lifecycle,
                                                ClusterRepositoryPort clusters,
                                                ClusterCredentialRepositoryPort credentials,
                                                SecretCryptoPort crypto, HelmReleaseCommandRunner helmRunner,
                                                HelmCredentialKubeconfigFactory kubeconfigFactory,
                                                ManagedApplicationRepositoryPort applications,
                                                AsyncJobRepositoryPort jobs,
                                                ApplicationDeploymentExecutorPort executor,
                                                ApplicationOperationConcurrencyPolicy concurrencyPolicy,
                                                RenderedManifestSanitizationPort manifestSanitizer,
                                                ApplicationRuntimeInspectionPort runtimeInspection,
                                                ApplicationExposurePort exposure, HelmReleaseStoragePreflight helmStoragePreflight,
                                                ObjectMapper objectMapper, Clock clock) {
        this.catalog = catalog;
        this.lifecycle = lifecycle;
        this.clusters = clusters;
        this.credentials = credentials;
        this.crypto = crypto;
        this.helmRunner = helmRunner;
        this.kubeconfigFactory = kubeconfigFactory;
        this.applications = applications;
        this.jobs = jobs;
        this.executor = executor;
        this.concurrencyPolicy = concurrencyPolicy;
        this.manifestSanitizer = manifestSanitizer;
        this.runtimeInspection = runtimeInspection;
        this.exposure = exposure;
        this.helmStoragePreflight = helmStoragePreflight;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** ApplicationDeliveryDeploymentService의 preview 처리에 필요한 업무 로직을 수행한다. */
    public DeploymentPlan preview(UUID tenantId, UUID applicationId, UUID clusterId, UUID chartVersionId, UUID valuesRevisionId,
                                  String namespace, String releaseName, boolean createNamespace, String exposureType, String hostname,
                                  String exposurePath, String backendServiceNamespace, String backendServiceName, Integer backendServicePort,
                                  String gatewayName, String gatewayNamespace,
                                  String actor) {
        var cluster = clusters.findById(clusterId).orElseThrow();
        if (!cluster.tenantId().equals(tenantId)) throw new NoSuchElementException("Cluster not found");
        ManagedApplication upgraded = applicationId == null ? null : requireApplication(tenantId, applicationId);
        if (upgraded != null && (!upgraded.clusterId().equals(clusterId) || !upgraded.namespace().equals(namespace)
                || !upgraded.helmReleaseName().equals(releaseName)))
            throw new IllegalArgumentException("Upgrade target, namespace and release name cannot be changed");
        ApplicationExposureMode exposureMode = ApplicationExposureMode.fromNullable(exposureType);
        validateTarget(namespace, releaseName, exposureMode, hostname, exposurePath, backendServiceName,
                backendServicePort, gatewayName, gatewayNamespace);
        String serviceNamespace = backendServiceNamespace == null || backendServiceNamespace.isBlank()
                ? namespace : backendServiceNamespace;
        KubernetesConnectionCredential connectionCredential = connectionCredential(clusterId);
        helmStoragePreflight.require(connectionCredential, namespace);
        var version = catalog.findVersion(tenantId, chartVersionId).orElseThrow();
        String values = valuesRevisionId == null ? null : decryptedValues(tenantId, valuesRevisionId);
        String manifest = helmRunner.render(releaseName, namespace, catalog.loadArtifact(tenantId, chartVersionId), values);
        RenderedExposureInspector.requireValidServices(manifest, namespace);
        if (exposureMode == ApplicationExposureMode.CHART_MANAGED) {
            RenderedExposureInspector.requireChartManagedExposure(manifest);
        } else if (exposureMode == ApplicationExposureMode.HTTP_ROUTE || exposureMode == ApplicationExposureMode.INGRESS) {
            if (serviceNamespace.equals(namespace))
                RenderedExposureInspector.requireHttpService(manifest, namespace, backendServiceName, backendServicePort);
            else if (exposureMode == ApplicationExposureMode.INGRESS)
                throw new IllegalArgumentException("Ingress backend Service must be in the application namespace");
            else exposure.requireBackendReference(connectionCredential, namespace, serviceNamespace,
                        backendServiceName, backendServicePort, "HTTPRoute");
            if (exposureMode == ApplicationExposureMode.HTTP_ROUTE)
            requireGateway(exposure.discoverHttpGateways(connectionCredential(clusterId), namespace),
                    gatewayNamespace, gatewayName);
        } else if (exposureMode == ApplicationExposureMode.TCP_ROUTE) {
            if (serviceNamespace.equals(namespace))
                RenderedExposureInspector.requireService(manifest, namespace, backendServiceName, backendServicePort);
            else exposure.requireBackendReference(connectionCredential, namespace, serviceNamespace,
                    backendServiceName, backendServicePort, "TCPRoute");
            requireGateway(exposure.discoverTcpGateways(connectionCredential, namespace), gatewayNamespace, gatewayName);
        }
        List<String> warnings = scanRisks(manifest);
        Instant now = clock.instant();
        String operation = upgraded == null ? "DEPLOY" : "UPGRADE";
        DeploymentPlan plan = new DeploymentPlan(UUID.randomUUID(), applicationId, clusterId, chartVersionId, valuesRevisionId,
                namespace, releaseName, createNamespace, exposureMode.name(), hostname,
                exposurePath, serviceNamespace, backendServiceName, backendServicePort, gatewayName, gatewayNamespace,
                manifestSanitizer.sanitize(manifest),
                sha256(manifest.getBytes(StandardCharsets.UTF_8)), json(warnings),
                operation + " " + releaseName + " TO " + cluster.name() + "/" + namespace,
                actor, now, now.plus(Duration.ofMinutes(15)), null);
        return lifecycle.savePlan(plan);
    }

    /** ApplicationDeliveryDeploymentService의 targetOptions 처리에 필요한 업무 로직을 수행한다. */
    public DeploymentTargetOptions targetOptions(UUID tenantId, UUID clusterId, UUID chartVersionId,
                                                 UUID valuesRevisionId, String namespace, String releaseName,
                                                 String exposureType) {
        var cluster = clusters.findById(clusterId).orElseThrow();
        if (!cluster.tenantId().equals(tenantId)) throw new NoSuchElementException("Cluster not found");
        catalog.findVersion(tenantId, chartVersionId).orElseThrow();
        String values = valuesRevisionId == null ? null : decryptedValues(tenantId, valuesRevisionId);
        String manifest = helmRunner.render(releaseName, namespace, catalog.loadArtifact(tenantId, chartVersionId), values);
        RenderedExposureInspector.requireValidServices(manifest, namespace);
        List<RenderedServiceOption> serviceOptions = RenderedExposureInspector.services(manifest, namespace).stream()
                .map(item -> new RenderedServiceOption(item.namespace(), item.name(), item.type(), item.portName(),
                        item.port(), item.targetPort(), item.nodePort(), item.protocol(), item.appProtocol(),
                        item.httpRouteCompatibility(), item.compatibilityMessage()))
                .toList();
        ApplicationExposureMode mode = ApplicationExposureMode.fromNullable(exposureType);
        ApplicationExposurePort.GatewayDiscovery gatewayDiscovery = mode == ApplicationExposureMode.TCP_ROUTE
                ? exposure.discoverTcpGateways(connectionCredential(clusterId), namespace)
                : mode == ApplicationExposureMode.HTTP_ROUTE
                ? exposure.discoverHttpGateways(connectionCredential(clusterId), namespace)
                : new ApplicationExposurePort.GatewayDiscovery("EMPTY", null, List.of());
        return new DeploymentTargetOptions(serviceOptions, gatewayDiscovery);
    }

    /** ApplicationDeliveryDeploymentService의 deploy 처리에 필요한 업무 로직을 수행한다. */
    @Transactional
    public DeploymentAccepted deploy(UUID tenantId, UUID planId, String confirmation, String actor) {
        DeploymentPlan plan = lifecycle.findPlan(tenantId, planId).orElseThrow();
        if (!plan.executableAt(clock.instant())) throw new IllegalStateException("Deployment plan expired or was already used");
        if (!plan.confirmationText().equals(confirmation == null ? "" : confirmation.trim())) {
            throw new IllegalArgumentException("Deployment confirmation text does not match");
        }
        if (!lifecycle.consumePlan(plan.id(), clock.instant())) throw new IllegalStateException("Deployment plan is no longer executable");
        boolean upgrade = plan.applicationId() != null;
        ManagedApplication application = upgrade ? requireApplicationForUpdate(tenantId, plan.applicationId())
                : ManagedApplication.helmChart(plan.clusterId(), plan.namespace(), plan.releaseName(), plan.releaseName(),
                plan.chartVersionId().toString(), actor).withReleaseMetadata(null, plan.chartVersionId(), plan.valuesRevisionId());
        if (upgrade) concurrencyPolicy.requireOperationAvailable(application);
        ManagedApplication pendingApplication = application.withStatus(upgrade ? ApplicationStatus.UPGRADING
                : ApplicationStatus.DEPLOYING, upgrade ? "HELM_UPGRADE_QUEUED" : "HELM_INSTALL_QUEUED", null);
        // 신규 Application의 작업 이력이 JDBC 외래키를 참조하기 전에 INSERT를 확정한다.
        try {
            application = upgrade ? applications.save(pendingApplication) : applications.saveAndFlush(pendingApplication);
        } catch (DataIntegrityViolationException exception) {
            throw new ApplicationOperationConflictException(
                    "An application with the same cluster, namespace and release name already exists", exception);
        }
        // JDBC 작업 이력의 async_job_id 외래키가 참조할 수 있도록 Job INSERT도 즉시 확정한다.
        AsyncJob job = jobs.saveAndFlush(AsyncJob.pending(upgrade ? AsyncJobType.HELM_UPGRADE : AsyncJobType.HELM_INSTALL));
        String operationType = upgrade ? "UPGRADE" : "INSTALL";
        ReleaseOperation operation = lifecycle.saveOperation(new ReleaseOperation(UUID.randomUUID(), application.id(),
                job.id(), operationType, "PENDING", null, null, null, actor, clock.instant(), null));
        try {
            executor.submit(plan.id(), tenantId, application.id(), job.id(), operation.id());
        } catch (RuntimeException exception) {
            job.markSubmissionFailed(clock.instant(), exception.getMessage());
            jobs.save(job);
            applications.save(application.withStatus(ApplicationStatus.FAILED, "HELM_INSTALL_REJECTED", safeMessage(exception)));
            lifecycle.saveOperation(new ReleaseOperation(operation.id(), application.id(), job.id(), operationType, "FAILED",
                    null, null, safeMessage(exception), actor, operation.requestedAt(), clock.instant()));
            throw exception;
        }
        return new DeploymentAccepted(application.id(), job.id(), operation.id());
    }

    /** ApplicationDeliveryDeploymentService의 executeInstall 처리의 핵심 작업 흐름을 실행한다. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void executeInstall(UUID planId, UUID tenantId, UUID applicationId, UUID jobId, UUID operationId) {
        AsyncJob job = jobs.findById(jobId).orElseThrow();
        ManagedApplication application = applications.findById(applicationId).orElseThrow();
        DeploymentPlan plan = lifecycle.findPlan(tenantId, planId).orElseThrow();
        String operationType = plan.applicationId() == null ? "INSTALL" : "UPGRADE";
        Instant started = clock.instant();
        job.markRunning(started);
        jobs.save(job);
        lifecycle.saveOperation(new ReleaseOperation(operationId, applicationId, jobId, operationType, "RUNNING",
                null, null, null, plan.createdBy(), started, null));
        try {
            KubernetesConnectionCredential connectionCredential = connectionCredential(plan.clusterId());
            helmStoragePreflight.require(connectionCredential, plan.namespace());
            if (ApplicationExposureMode.fromNullable(plan.exposureType()) == ApplicationExposureMode.HTTP_ROUTE) {
                // Preview 이후 Gateway가 삭제되거나 준비 상태가 바뀐 경우 Helm 변경 전에 중단한다.
                requireGateway(exposure.discoverHttpGateways(connectionCredential, plan.namespace()),
                        plan.gatewayNamespace(), plan.gatewayName());
            }
            if (ApplicationExposureMode.fromNullable(plan.exposureType()) == ApplicationExposureMode.TCP_ROUTE)
                requireGateway(exposure.discoverTcpGateways(connectionCredential, plan.namespace()),
                        plan.gatewayNamespace(), plan.gatewayName());
            helmRunner.install(plan, catalog.loadArtifact(tenantId, plan.chartVersionId()),
                    plan.valuesRevisionId() == null ? null : decryptedValues(tenantId, plan.valuesRevisionId()),
                    kubeconfig(plan.clusterId()));
            String exposureSummary = applyExposure(plan, tenantId, applicationId);
            Instant completed = clock.instant();
            int revision = application.currentReleaseRevision() == null ? 1 : application.currentReleaseRevision() + 1;
            lifecycle.saveRelease(new ApplicationRelease(UUID.randomUUID(), applicationId, revision, plan.chartVersionId(),
                    plan.valuesRevisionId(), plan.manifestSha256(), "DEPLOYED", plan.createdBy(), completed));
            ApplicationStatus installedStatus = exposureSummary == null
                    ? ApplicationStatus.RUNNING : ApplicationStatus.RUNNING_ENDPOINT_DEGRADED;
            applications.save(application.withReleaseMetadata(revision, plan.chartVersionId(), plan.valuesRevisionId())
                    .withStatus(installedStatus, "HELM_INSTALL_SUCCEEDED", exposureSummary));
            job.markSucceeded(completed);
            jobs.save(job);
            lifecycle.saveOperation(new ReleaseOperation(operationId, applicationId, jobId, operationType, "SUCCEEDED",
                    revision, exposureSummary == null ? "Helm " + operationType.toLowerCase() + " completed" : "Helm completed; " + exposureSummary,
                    null, plan.createdBy(), started, completed));
        } catch (RuntimeException exception) {
            Instant completed = clock.instant();
            applications.save(application.withStatus(ApplicationStatus.FAILED, "HELM_INSTALL_FAILED", safeMessage(exception)));
            job.markFailed(completed, "HELM_INSTALL_FAILED", safeMessage(exception));
            jobs.save(job);
            lifecycle.saveOperation(new ReleaseOperation(operationId, applicationId, jobId, operationType, "FAILED",
                    null, null, safeMessage(exception), plan.createdBy(), started, completed));
        }
    }

    /** ApplicationDeliveryDeploymentService의 applications 처리에 필요한 업무 로직을 수행한다. */
    @Transactional(readOnly = true)
    public List<ManagedApplication> applications(UUID tenantId) {
        List<UUID> clusterIds = clusters.findAll(tenantId, null).stream().map(cluster -> cluster.id()).toList();
        return applications.findRecentByClusterIds(clusterIds, 200);
    }

    /** ApplicationDeliveryDeploymentService의 application 처리에 필요한 업무 로직을 수행한다. */
    @Transactional(readOnly = true)
    public ManagedApplication application(UUID tenantId, UUID applicationId) {
        return requireApplication(tenantId, applicationId);
    }

    /** ApplicationDeliveryDeploymentService의 plan 처리에 필요한 업무 로직을 수행한다. */
    @Transactional(readOnly = true)
    public DeploymentPlan plan(UUID tenantId, UUID planId) {
        return lifecycle.findPlan(tenantId, planId).orElseThrow();
    }

    /** ApplicationDeliveryDeploymentService의 runtime 처리의 핵심 작업 흐름을 실행한다. */
    @Transactional(readOnly = true)
    public ApplicationRuntimeInspectionPort.RuntimeOverview runtime(UUID tenantId, UUID applicationId) {
        ManagedApplication application = requireApplication(tenantId, applicationId);
        EncryptedClusterCredential stored = credentials.findByClusterId(application.clusterId())
                .orElseThrow(() -> new NoSuchElementException("Cluster credential not found"));
        String payload = crypto.decrypt(new EncryptedSecret(stored.encryptedPayload(), stored.keyId(),
                stored.algorithm(), stored.nonce()));
        var observed = runtimeInspection.inspect(new KubernetesConnectionCredential(stored.credentialType(), payload),
                application.namespace(), application.helmReleaseName());
        // 동적 조회 결과를 우선해 생성 직후 저장된 APPLIED 상태가 최신 READY 상태를 덮지 않게 한다.
        Map<String, ApplicationRuntimeInspectionPort.Endpoint> endpoints = new LinkedHashMap<>();
        observed.endpoints().forEach(item -> endpoints.put(endpointKey(item), item));
        lifecycle.findEndpoints(tenantId, applicationId).forEach(item -> {
            var endpoint = new ApplicationRuntimeInspectionPort.Endpoint(item.endpointType(), item.hostname(),
                    item.url(), item.status(), item.hostname(), null, null, null, "EXTERNAL_DOMAIN");
            endpoints.putIfAbsent(endpointKey(endpoint), endpoint);
        });
        return new ApplicationRuntimeInspectionPort.RuntimeOverview(observed.readyPods(), observed.totalPods(),
                observed.restarts(), observed.workloads(), List.copyOf(endpoints.values()));
    }

    /** ApplicationDeliveryDeploymentService의 operations 처리에 필요한 업무 로직을 수행한다. */
    @Transactional(readOnly = true)
    public List<ReleaseOperation> operations(UUID tenantId, UUID applicationId) {
        return lifecycle.findOperations(tenantId, applicationId, 100);
    }

    /** ApplicationDeliveryDeploymentService의 releases 처리에 필요한 업무 로직을 수행한다. */
    @Transactional(readOnly = true)
    public List<ApplicationRelease> releases(UUID tenantId, UUID applicationId) {
        return lifecycle.findReleases(tenantId, applicationId, 100);
    }

    /** ApplicationDeliveryDeploymentService의 rollbackConfirmation 처리에 필요한 업무 로직을 수행한다. */
    @Transactional(readOnly = true)
    public LifecycleConfirmation rollbackConfirmation(UUID tenantId, UUID applicationId, int revision) {
        ManagedApplication application = requireApplication(tenantId, applicationId);
        if (revision < 1) throw new IllegalArgumentException("Rollback revision must be positive");
        if (lifecycle.findReleases(tenantId, applicationId, 100).stream().noneMatch(item -> item.revision() == revision))
            throw new IllegalArgumentException("Rollback revision does not exist for this application");
        if (Integer.valueOf(revision).equals(application.currentReleaseRevision()))
            throw new IllegalArgumentException("Application is already at the requested revision");
        return new LifecycleConfirmation("ROLLBACK " + application.helmReleaseName() + " TO REVISION " + revision,
                "Helm release를 선택한 revision으로 되돌립니다. PVC와 Namespace는 유지됩니다.");
    }

    /** ApplicationDeliveryDeploymentService의 rollback 처리에 필요한 업무 로직을 수행한다. */
    @Transactional
    public DeploymentAccepted rollback(UUID tenantId, UUID applicationId, int revision, String confirmation, String actor) {
        ManagedApplication application = requireApplicationForUpdate(tenantId, applicationId);
        concurrencyPolicy.requireOperationAvailable(application);
        LifecycleConfirmation expected = rollbackConfirmation(tenantId, applicationId, revision);
        requireConfirmation(expected.confirmationText(), confirmation);
        return queueLifecycle(application, tenantId, AsyncJobType.HELM_ROLLBACK, "ROLLBACK", revision, actor);
    }

    /** ApplicationDeliveryDeploymentService의 uninstallConfirmation 처리에 필요한 업무 로직을 수행한다. */
    @Transactional(readOnly = true)
    public LifecycleConfirmation uninstallConfirmation(UUID tenantId, UUID applicationId) {
        ManagedApplication application = requireApplication(tenantId, applicationId);
        return new LifecycleConfirmation("UNINSTALL " + application.helmReleaseName(),
                "Helm release를 제거합니다. PVC·DNS companion·TLS Secret의 보존 여부를 선택할 수 있으며 공유 Namespace와 Tenant Chart Library는 삭제하지 않습니다.");
    }

    /** ApplicationDeliveryDeploymentService의 uninstall 처리에 필요한 업무 로직을 수행한다. */
    @Transactional
    public DeploymentAccepted uninstall(UUID tenantId, UUID applicationId, String confirmation, String actor,
                                        boolean preservePvcs, boolean preserveDns, boolean preserveTls) {
        ManagedApplication application = requireApplicationForUpdate(tenantId, applicationId);
        concurrencyPolicy.requireOperationAvailable(application);
        LifecycleConfirmation expected = uninstallConfirmation(tenantId, applicationId);
        requireConfirmation(expected.confirmationText(), confirmation);
        return queueLifecycle(application, tenantId, AsyncJobType.HELM_UNINSTALL, "UNINSTALL", null, actor,
                preservePvcs, preserveDns, preserveTls);
    }

    /** 실패한 uninstall/companion cleanup을 동일한 Application 범위에서 다시 예약한다. */
    @Transactional
    public DeploymentAccepted retryCleanup(UUID tenantId, UUID applicationId, String confirmation, String actor,
                                           boolean preservePvcs, boolean preserveDns, boolean preserveTls) {
        ManagedApplication application = requireApplicationForUpdate(tenantId, applicationId);
        if (application.status() != ApplicationStatus.FAILED)
            throw new IllegalStateException("Cleanup retry is only available for a failed application");
        LifecycleConfirmation expected = uninstallConfirmation(tenantId, applicationId);
        requireConfirmation(expected.confirmationText(), confirmation);
        return queueLifecycle(application, tenantId, AsyncJobType.HELM_UNINSTALL, "UNINSTALL_RETRY", null, actor,
                preservePvcs, preserveDns, preserveTls);
    }

    /** ApplicationDeliveryDeploymentService의 executeRollback 처리의 핵심 작업 흐름을 실행한다. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void executeRollback(UUID tenantId, UUID applicationId, int revision, UUID jobId, UUID operationId,
                                String actor) {
        executeLifecycle(tenantId, applicationId, jobId, operationId, actor, "ROLLBACK", revision);
    }

    /** ApplicationDeliveryDeploymentService의 executeUninstall 처리의 핵심 작업 흐름을 실행한다. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void executeUninstall(UUID tenantId, UUID applicationId, UUID jobId, UUID operationId, String actor) {
        executeUninstall(tenantId, applicationId, jobId, operationId, actor, true, false, true);
    }

    /** 선택형 보존 정책을 포함한 uninstall 작업을 transaction 밖에서 실행한다. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void executeUninstall(UUID tenantId, UUID applicationId, UUID jobId, UUID operationId, String actor,
                                 boolean preservePvcs, boolean preserveDns, boolean preserveTls) {
        executeLifecycle(tenantId, applicationId, jobId, operationId, actor, "UNINSTALL", null,
                preservePvcs, preserveDns, preserveTls);
    }

    /** ApplicationDeliveryDeploymentService의 queueLifecycle 처리에 필요한 업무 로직을 수행한다. */
    private DeploymentAccepted queueLifecycle(ManagedApplication application, UUID tenantId, AsyncJobType jobType,
                                               String operationType, Integer revision, String actor) {
        return queueLifecycle(application, tenantId, jobType, operationType, revision, actor, true, false, true);
    }

    /** Lifecycle 작업과 삭제 보존 정책을 비동기 worker에 함께 전달한다. */
    private DeploymentAccepted queueLifecycle(ManagedApplication application, UUID tenantId, AsyncJobType jobType,
                                               String operationType, Integer revision, String actor,
                                               boolean preservePvcs, boolean preserveDns, boolean preserveTls) {
        ApplicationStatus queuedStatus = "ROLLBACK".equals(operationType)
                ? ApplicationStatus.ROLLING_BACK : ApplicationStatus.UNINSTALLING;
        applications.save(application.withStatus(queuedStatus, "HELM_" + operationType + "_QUEUED", null));
        // Lifecycle 작업 이력도 같은 외래키 순서를 사용하므로 Job INSERT를 먼저 확정한다.
        AsyncJob job = jobs.saveAndFlush(AsyncJob.pending(jobType));
        ReleaseOperation operation = lifecycle.saveOperation(new ReleaseOperation(UUID.randomUUID(), application.id(),
                job.id(), operationType, "PENDING", revision, null, null, actor, clock.instant(), null));
        if ("ROLLBACK".equals(operationType)) {
            executor.submitRollback(tenantId, application.id(), revision, job.id(), operation.id(), actor);
        } else {
            executor.submitUninstall(tenantId, application.id(), job.id(), operation.id(), actor,
                    preservePvcs, preserveDns, preserveTls);
        }
        return new DeploymentAccepted(application.id(), job.id(), operation.id());
    }

    /** ApplicationDeliveryDeploymentService의 executeLifecycle 처리의 핵심 작업 흐름을 실행한다. */
    private void executeLifecycle(UUID tenantId, UUID applicationId, UUID jobId, UUID operationId, String actor,
                                  String operationType, Integer revision) {
        executeLifecycle(tenantId, applicationId, jobId, operationId, actor, operationType, revision,
                true, false, true);
    }

    /** Helm lifecycle과 companion/data cleanup 정책을 한 작업 단위로 실행한다. */
    private void executeLifecycle(UUID tenantId, UUID applicationId, UUID jobId, UUID operationId, String actor,
                                  String operationType, Integer revision, boolean preservePvcs,
                                  boolean preserveDns, boolean preserveTls) {
        AsyncJob job = jobs.findById(jobId).orElseThrow();
        ManagedApplication application = requireApplication(tenantId, applicationId);
        Instant started = clock.instant();
        job.markRunning(started);
        jobs.save(job);
        lifecycle.saveOperation(new ReleaseOperation(operationId, applicationId, jobId, operationType, "RUNNING",
                revision, null, null, actor, started, null));
        try {
            KubernetesConnectionCredential credential = connectionCredential(application.clusterId());
            if ("UNINSTALL".equals(operationType)) exposure.prepareUninstall(credential, application.namespace(),
                    application.helmReleaseName(), preservePvcs, preserveTls);
            helmRunner.lifecycle(application, operationType, revision, kubeconfig(application.clusterId()));
            if ("UNINSTALL".equals(operationType)) {
                if (!preserveDns) deleteExposure(application);
                exposure.finalizeUninstall(credential, application.namespace(), application.helmReleaseName(),
                        preservePvcs, preserveTls);
            }
            Instant completed = clock.instant();
            if ("UNINSTALL".equals(operationType)) {
                // 성공 Job은 감사 가능한 최소 실행 증거로 남기고 Application 상세 데이터는 함께 제거한다.
                lifecycle.deleteApplicationGraph(application.id());
                job.markSucceeded(completed);
                jobs.save(job);
                return;
            }
            ManagedApplication completedApplication = application;
            if ("ROLLBACK".equals(operationType)) {
                ApplicationRelease target = lifecycle.findReleases(tenantId, applicationId, 100).stream()
                        .filter(item -> item.revision() == revision).findFirst().orElseThrow();
                completedApplication = application.withReleaseMetadata(revision, target.chartVersionId(), target.valuesRevisionId());
            }
            applications.save(completedApplication.withStatus(ApplicationStatus.RUNNING,
                    "HELM_" + operationType + "_SUCCEEDED", null));
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

    /** ApplicationDeliveryDeploymentService의 kubeconfig 처리에 필요한 업무 로직을 수행한다. */
    private String kubeconfig(UUID clusterId) {
        EncryptedClusterCredential stored = credentials.findByClusterId(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster credential not found"));
        String payload = crypto.decrypt(new EncryptedSecret(stored.encryptedPayload(), stored.keyId(),
                stored.algorithm(), stored.nonce()));
        return kubeconfigFactory.create(stored.credentialType(), payload);
    }

    /** ApplicationDeliveryDeploymentService의 decryptedValues 처리에 필요한 업무 로직을 수행한다. */
    private String decryptedValues(UUID tenantId, UUID revisionId) {
        return crypto.decrypt(catalog.findRevision(tenantId, revisionId).orElseThrow().encryptedValues());
    }

    /** ApplicationDeliveryDeploymentService의 requireApplication 처리 입력과 현재 상태의 유효성을 검증한다. */
    private ManagedApplication requireApplication(UUID tenantId, UUID applicationId) {
        ManagedApplication application = applications.findById(applicationId).orElseThrow();
        requireApplicationTenant(tenantId, application);
        return application;
    }

    /** ApplicationDeliveryDeploymentService의 requireApplicationForUpdate 처리 입력과 현재 상태의 유효성을 검증한다. */
    private ManagedApplication requireApplicationForUpdate(UUID tenantId, UUID applicationId) {
        ManagedApplication application = applications.findByIdForUpdate(applicationId).orElseThrow();
        requireApplicationTenant(tenantId, application);
        return application;
    }

    /** ApplicationDeliveryDeploymentService의 requireApplicationTenant 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void requireApplicationTenant(UUID tenantId, ManagedApplication application) {
        var cluster = clusters.findById(application.clusterId()).orElseThrow();
        if (!cluster.tenantId().equals(tenantId)) throw new NoSuchElementException("Application not found");
    }

    /** ApplicationDeliveryDeploymentService의 requireConfirmation 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void requireConfirmation(String expected, String actual) {
        if (!expected.equals(actual == null ? "" : actual.trim()))
            throw new IllegalArgumentException("Lifecycle confirmation text does not match");
    }

    /** ApplicationDeliveryDeploymentService의 validateTarget 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void validateTarget(String namespace, String releaseName, ApplicationExposureMode exposureMode, String hostname,
                                String exposurePath, String backendServiceName, Integer backendServicePort,
                                String gatewayName, String gatewayNamespace) {
        if (namespace == null || namespace.length() > 63 || !DNS_LABEL.matcher(namespace).matches())
            throw new IllegalArgumentException("Namespace must be a valid DNS label");
        if (releaseName == null || releaseName.length() > 53 || !DNS_LABEL.matcher(releaseName).matches())
            throw new IllegalArgumentException("Release name must be a valid Helm release name");
        if ((exposureMode == ApplicationExposureMode.HTTP_ROUTE || exposureMode == ApplicationExposureMode.INGRESS)
                && (hostname == null || hostname.length() > 253 || !HOSTNAME.matcher(hostname).matches()))
            throw new IllegalArgumentException("A valid hostname is required for HTTPRoute exposure");
        if (exposureMode == ApplicationExposureMode.HTTP_ROUTE || exposureMode == ApplicationExposureMode.INGRESS
                || exposureMode == ApplicationExposureMode.TCP_ROUTE) {
            if (exposureMode != ApplicationExposureMode.TCP_ROUTE
                    && (exposurePath == null || !exposurePath.startsWith("/") || exposurePath.length() > 255))
                throw new IllegalArgumentException("Exposure path must start with /");
            if (!validDnsLabel(backendServiceName) || backendServicePort == null || backendServicePort < 1 || backendServicePort > 65535)
                throw new IllegalArgumentException("A valid backend Service and port are required");
            if (exposureMode != ApplicationExposureMode.INGRESS
                    && (!validDnsLabel(gatewayName) || !validDnsLabel(gatewayNamespace)))
                throw new IllegalArgumentException("A valid Gateway name and namespace are required");
        }
    }

    /** ApplicationDeliveryDeploymentService의 validDnsLabel 처리에 필요한 업무 로직을 수행한다. */
    private boolean validDnsLabel(String value) {
        return value != null && value.length() <= 63 && DNS_LABEL.matcher(value).matches();
    }

    /** ApplicationDeliveryDeploymentService의 applyExposure 처리에 필요한 업무 로직을 수행한다. */
    private String applyExposure(DeploymentPlan plan, UUID tenantId, UUID applicationId) {
        ApplicationExposureMode mode = ApplicationExposureMode.fromNullable(plan.exposureType());
        if (mode != ApplicationExposureMode.HTTP_ROUTE && mode != ApplicationExposureMode.INGRESS
                && mode != ApplicationExposureMode.TCP_ROUTE) return null;
        KubernetesConnectionCredential credential = null;
        try {
            credential = connectionCredential(plan.clusterId());
            var result = mode == ApplicationExposureMode.HTTP_ROUTE ? exposure.applyHttpRoute(credential,
                    new ApplicationExposurePort.HttpRouteRequest(plan.namespace(), routeName(plan.releaseName()),
                            plan.gatewayNamespace(), plan.gatewayName(), plan.hostname(), plan.exposurePath(),
                            plan.backendServiceNamespace(), plan.backendServiceName(), plan.backendServicePort()))
                    : mode == ApplicationExposureMode.INGRESS ? exposure.applyIngress(credential,
                    new ApplicationExposurePort.IngressRequest(plan.namespace(), routeName(plan.releaseName()),
                            plan.hostname(), plan.exposurePath(), plan.backendServiceName(), plan.backendServicePort()))
                    : exposure.applyTcpRoute(credential, new ApplicationExposurePort.TcpRouteRequest(plan.namespace(),
                    routeName(plan.releaseName()), plan.gatewayNamespace(), plan.gatewayName(),
                    plan.backendServiceNamespace(), plan.backendServiceName(), plan.backendServicePort()));
            Instant now = clock.instant();
            lifecycle.saveEndpoint(new ApplicationEndpoint(UUID.randomUUID(), applicationId, mode.name(), result.url(),
                    plan.hostname(), result.status(), now, now));
            return null;
        } catch (RuntimeException exception) {
            // apply가 중간에 실패했을 가능성까지 고려해 companion resource를 best-effort로 정리한다.
            try {
                if (credential != null) {
                    if (mode == ApplicationExposureMode.HTTP_ROUTE) exposure.deleteHttpRoute(credential, plan.namespace(), routeName(plan.releaseName()));
                    else if (mode == ApplicationExposureMode.INGRESS) exposure.deleteIngress(credential, plan.namespace(), routeName(plan.releaseName()));
                    else exposure.deleteTcpRoute(credential, plan.namespace(), routeName(plan.releaseName()));
                }
            } catch (RuntimeException ignored) {
                // 원래 실패 원인을 작업 이력에 남기기 위해 정리 오류는 덮어쓰지 않는다.
            }
            return mode + " apply failed: " + safeMessage(exception);
        }
    }

    /** ApplicationDeliveryDeploymentService의 deleteExposure 처리 대상과 관련 상태를 안전하게 정리한다. */
    private void deleteExposure(ManagedApplication application) {
        var endpoints = lifecycle.findEndpoints(clusters.findById(application.clusterId()).orElseThrow().tenantId(), application.id());
        var credential = connectionCredential(application.clusterId());
        if (endpoints.stream().anyMatch(item -> "HTTP_ROUTE".equals(item.endpointType()))) {
            exposure.deleteHttpRoute(credential, application.namespace(),
                    routeName(application.helmReleaseName()));
        }
        if (endpoints.stream().anyMatch(item -> "INGRESS".equals(item.endpointType())))
            exposure.deleteIngress(credential, application.namespace(), routeName(application.helmReleaseName()));
        if (endpoints.stream().anyMatch(item -> "TCP_ROUTE".equals(item.endpointType())))
            exposure.deleteTcpRoute(credential, application.namespace(), routeName(application.helmReleaseName()));
    }

    /** ApplicationDeliveryDeploymentService의 routeName 처리에 필요한 업무 로직을 수행한다. */
    private String routeName(String releaseName) { return releaseName + "-klueops"; }

    /** ApplicationDeliveryDeploymentService의 requireGateway 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void requireGateway(ApplicationExposurePort.GatewayDiscovery discovery, String namespace, String name) {
        if (!"AVAILABLE".equals(discovery.status())) {
            throw new IllegalArgumentException(discovery.message() == null
                    ? "HTTPRoute Gateway discovery is unavailable" : discovery.message());
        }
        ApplicationExposurePort.GatewayOption gateway = discovery.gateways().stream()
                .filter(item -> item.namespace().equals(namespace) && item.name().equals(name))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "Selected HTTPRoute Gateway does not exist or has no HTTP/HTTPS listener"));
        if (!"READY".equals(gateway.readiness())) {
            throw new IllegalArgumentException("Selected HTTPRoute Gateway is not ready");
        }
    }

    /** ApplicationDeliveryDeploymentService의 endpointKey 처리에 필요한 업무 로직을 수행한다. */
    private String endpointKey(ApplicationRuntimeInspectionPort.Endpoint endpoint) {
        return endpoint.type() + "|" + endpoint.url();
    }

    /** ApplicationDeliveryDeploymentService의 connectionCredential 처리에 필요한 업무 로직을 수행한다. */
    private KubernetesConnectionCredential connectionCredential(UUID clusterId) {
        EncryptedClusterCredential stored = credentials.findByClusterId(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster credential not found"));
        String payload = crypto.decrypt(new EncryptedSecret(stored.encryptedPayload(), stored.keyId(),
                stored.algorithm(), stored.nonce()));
        return new KubernetesConnectionCredential(stored.credentialType(), payload);
    }

    /** ApplicationDeliveryDeploymentService의 scanRisks 처리에 필요한 업무 로직을 수행한다. */
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

    /** ApplicationDeliveryDeploymentService의 json 처리에 필요한 업무 로직을 수행한다. */
    private String json(List<String> warnings) {
        try { return objectMapper.writeValueAsString(warnings); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Warnings serialization failed", exception); }
    }

    /** ApplicationDeliveryDeploymentService의 sha256 처리에 필요한 업무 로직을 수행한다. */
    private String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    /** ApplicationDeliveryDeploymentService의 bounded 처리에 필요한 업무 로직을 수행한다. */
    private String bounded(String value) {
        if (value == null) return "Unknown Helm error";
        return value.length() <= 1800 ? value : value.substring(0, 1800);
    }

    /** ApplicationDeliveryDeploymentService의 safeMessage 처리에 필요한 업무 로직을 수행한다. */
    private String safeMessage(RuntimeException exception) { return bounded(exception.getMessage()); }

    public record DeploymentAccepted(UUID applicationId, UUID jobId, UUID operationId) { }
    public record LifecycleConfirmation(String confirmationText, String impactSummary) { }
    public record DeploymentTargetOptions(List<RenderedServiceOption> services,
                                          ApplicationExposurePort.GatewayDiscovery gatewayDiscovery) { }
    public record RenderedServiceOption(String namespace, String name, String type, String portName, int port,
                                        String targetPort, Integer nodePort, String protocol, String appProtocol,
                                        String httpRouteCompatibility, String compatibilityMessage) { }
}
