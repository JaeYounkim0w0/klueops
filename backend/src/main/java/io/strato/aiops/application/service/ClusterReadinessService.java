package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.client.Config;
import io.strato.aiops.application.port.in.ClusterReadinessReport;
import io.strato.aiops.application.port.in.ClusterReadinessUseCase;
import io.strato.aiops.application.port.in.GetClusterSnapshotUseCase;
import io.strato.aiops.application.port.out.ClusterCredentialRepositoryPort;
import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.application.port.out.KubernetesAccessReviewResult;
import io.strato.aiops.application.port.out.KubernetesClusterPort;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesConnectionTestResult;
import io.strato.aiops.application.port.out.KubernetesMutationPort;
import io.strato.aiops.application.port.out.KubernetesNode;
import io.strato.aiops.application.port.out.SecretCryptoPort;
import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import io.strato.aiops.domain.cluster.EncryptedClusterCredential;
import io.strato.aiops.domain.cluster.EncryptedSecret;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
public class ClusterReadinessService implements ClusterReadinessUseCase {

    private static final String DEFAULT_NAMESPACE = "default";
    private static final KubernetesUpgradeCatalog UPGRADE_CATALOG = KubernetesUpgradeCatalog.loadDefault();

    private final ClusterRepositoryPort clusterRepository;
    private final ClusterCredentialRepositoryPort credentialRepository;
    private final SecretCryptoPort secretCryptoPort;
    private final KubernetesClusterPort kubernetesClusterPort;
    private final KubernetesMutationPort kubernetesMutationPort;
    private final GetClusterSnapshotUseCase clusterSnapshotUseCase;
    private final ObjectMapper objectMapper;
    private final Executor readinessProbeExecutor;
    private final MeterRegistry meterRegistry;
    private final Duration cacheTtl;
    private final ConcurrentHashMap<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();

    /** ClusterReadinessService 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public ClusterReadinessService(
            ClusterRepositoryPort clusterRepository,
            ClusterCredentialRepositoryPort credentialRepository,
            SecretCryptoPort secretCryptoPort,
            KubernetesClusterPort kubernetesClusterPort,
            KubernetesMutationPort kubernetesMutationPort,
            GetClusterSnapshotUseCase clusterSnapshotUseCase,
            ObjectMapper objectMapper,
            @Qualifier("readinessProbeExecutor") Executor readinessProbeExecutor,
            MeterRegistry meterRegistry,
            @Value("${aiops.readiness.cache-seconds:300}") long cacheSeconds
    ) {
        this.clusterRepository = clusterRepository;
        this.credentialRepository = credentialRepository;
        this.secretCryptoPort = secretCryptoPort;
        this.kubernetesClusterPort = kubernetesClusterPort;
        this.kubernetesMutationPort = kubernetesMutationPort;
        this.clusterSnapshotUseCase = clusterSnapshotUseCase;
        this.objectMapper = objectMapper;
        this.readinessProbeExecutor = readinessProbeExecutor;
        this.meterRegistry = meterRegistry;
        this.cacheTtl = Duration.ofSeconds(Math.max(30, cacheSeconds));
    }

    /** ClusterReadinessService의 getReadiness 처리 결과를 조회해 반환한다. */
    @Override
    public ClusterReadinessReport getReadiness(UUID clusterId, String namespace, String targetVersion, boolean refresh) {
        long startedAt = System.nanoTime();
        String effectiveNamespace = textOrDefault(namespace, DEFAULT_NAMESPACE);
        String effectiveTarget = normalizeVersion(targetVersion);
        CacheKey key = new CacheKey(clusterId, effectiveNamespace, effectiveTarget);
        CacheEntry cached = cache.get(key);
        if (!refresh && cached != null && cached.expiresAt().isAfter(Instant.now())) {
            meterRegistry.counter("aiops.cluster.readiness.cache", "result", "hit").increment();
            return cached.report();
        }
        meterRegistry.counter("aiops.cluster.readiness.cache", "result", "miss").increment();

        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster not found: " + clusterId));
        EncryptedClusterCredential encrypted = credentialRepository.findByClusterId(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster credential not found: " + clusterId));
        String payload = secretCryptoPort.decrypt(new EncryptedSecret(encrypted.encryptedPayload(), encrypted.keyId(),
                encrypted.algorithm(), encrypted.nonce()));
        KubernetesConnectionCredential credential = new KubernetesConnectionCredential(encrypted.credentialType(), payload);

        KubernetesConnectionTestResult connection = kubernetesClusterPort.testConnection(credential);
        List<KubernetesNode> nodes = connection.reachable() ? safeNodes(credential) : List.of();
        Instant checkedAt = Instant.now();
        ClusterReadinessReport.CapabilityMatrix capabilities = assessCapabilities(credential, effectiveNamespace,
                connection.reachable());
        ClusterReadinessReport.CredentialHealth credentialHealth = assessCredential(encrypted, payload, connection, checkedAt);
        ClusterReadinessReport.UpgradeReadiness upgrade = assessUpgrade(clusterId, connection, nodes, effectiveTarget);
        int score = Math.round((capabilities.score() * 0.35f)
                + (credentialScore(credentialHealth.status()) * 0.35f)
                + (upgrade.score() * 0.30f));
        String overallStatus = statusForScore(score, connection.reachable());
        Instant expiresAt = checkedAt.plus(cacheTtl);
        ClusterReadinessReport report = new ClusterReadinessReport(cluster.id(), cluster.name(), overallStatus, score,
                capabilities, credentialHealth, upgrade, checkedAt, expiresAt);
        cache.put(key, new CacheEntry(report, expiresAt));
        Timer.builder("aiops.cluster.readiness.duration")
                .tag("status", overallStatus)
                .register(meterRegistry)
                .record(Duration.ofNanos(System.nanoTime() - startedAt));
        return report;
    }

    /** ClusterReadinessService의 assessCapabilities 처리에 필요한 업무 로직을 수행한다. */
    private ClusterReadinessReport.CapabilityMatrix assessCapabilities(KubernetesConnectionCredential credential,
                                                                        String namespace,
                                                                        boolean reachable) {
        List<CapabilitySpec> specs = capabilitySpecs();
        List<ClusterReadinessReport.CapabilityCheck> checks = specs.stream()
                .map(spec -> CompletableFuture.supplyAsync(
                        () -> assessCapability(credential, namespace, reachable, spec), readinessProbeExecutor)
                        .exceptionally(error -> unknownCapability(spec, namespace, error)))
                .toList().stream().map(CompletableFuture::join).toList();
        CapabilityMatrixSummary summary = CapabilityMatrixSummary.from(
                checks.stream().map(ClusterReadinessReport.CapabilityCheck::state).toList());
        String status = !reachable ? "UNKNOWN" : summary.status();
        return new ClusterReadinessReport.CapabilityMatrix(status, summary.allowed(), summary.denied(),
                summary.unknown(), summary.score(), List.copyOf(checks));
    }

    /** ClusterReadinessService의 assessCapability 처리에 필요한 업무 로직을 수행한다. */
    private ClusterReadinessReport.CapabilityCheck assessCapability(KubernetesConnectionCredential credential,
                                                                     String namespace,
                                                                     boolean reachable,
                                                                     CapabilitySpec spec) {
        if (!reachable) return unknownCapability(spec, namespace, null);
        KubernetesAccessReviewResult result = kubernetesMutationPort.canI(credential,
                spec.namespaceScoped() ? namespace : null, spec.verb(), spec.group(), spec.resource(),
                spec.subresource(), null);
        return new ClusterReadinessReport.CapabilityCheck(spec.id(), spec.category(), spec.displayName(), spec.verb(),
                spec.group(), spec.resource(), spec.namespaceScoped() ? namespace : null, result.allowed(),
                result.allowed() ? "ALLOWED" : "DENIED", result.reason(), "SELF_SUBJECT_ACCESS_REVIEW");
    }

    /** ClusterReadinessService의 unknownCapability 처리에 필요한 업무 로직을 수행한다. */
    private ClusterReadinessReport.CapabilityCheck unknownCapability(CapabilitySpec spec, String namespace,
                                                                      Throwable error) {
        String reason = error == null ? "Connection unavailable; capability could not be verified"
                : "Capability check failed: " + rootMessage(error);
        return new ClusterReadinessReport.CapabilityCheck(spec.id(), spec.category(), spec.displayName(), spec.verb(),
                spec.group(), spec.resource(), spec.namespaceScoped() ? namespace : null, false, "UNKNOWN", reason,
                "SELF_SUBJECT_ACCESS_REVIEW");
    }

    /** ClusterReadinessService의 assessCredential 처리에 필요한 업무 로직을 수행한다. */
    private ClusterReadinessReport.CredentialHealth assessCredential(EncryptedClusterCredential encrypted,
                                                                      String payload,
                                                                      KubernetesConnectionTestResult connection,
                                                                      Instant checkedAt) {
        CredentialLifetime lifetime = credentialLifetime(encrypted.credentialType(), payload);
        Instant expiresAt = lifetime.tokenExpiresAt();
        Long expiresInDays = expiresAt == null ? null : ChronoUnit.DAYS.between(checkedAt, expiresAt);
        long ageDays = Math.max(0, ChronoUnit.DAYS.between(encrypted.createdAt(), checkedAt));
        List<String> findings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        String status = "HEALTHY";

        if (!connection.reachable()) {
            status = "CRITICAL";
            findings.add("Kubernetes API authentication or connectivity check failed.");
            recommendations.add("Verify the API endpoint, CA trust, network route, and credential permissions.");
        }
        if (expiresInDays != null && expiresInDays < 0) {
            status = "CRITICAL";
            findings.add("The embedded bearer token is expired.");
            recommendations.add("Rotate the credential and run connection verification again.");
        } else if (expiresInDays != null && expiresInDays <= 14) {
            status = "CRITICAL";
            findings.add("The embedded bearer token expires within 14 days.");
            recommendations.add("Rotate the credential immediately and verify access with the replacement.");
        } else if (expiresInDays != null && expiresInDays <= 30) {
            if (!"CRITICAL".equals(status)) status = "WARNING";
            findings.add("The embedded bearer token expires within 30 days.");
            recommendations.add("Schedule credential rotation before token expiry.");
        }
        if (expiresAt == null && lifetime.clientCertificateExpiresAt() == null) {
            if (!"CRITICAL".equals(status)) status = "WARNING";
            findings.add("Token expiry is not observable from the stored credential.");
            recommendations.add("Use a short-lived credential or document an external rotation schedule.");
        }
        status = applyCertificateFinding("client certificate", lifetime.clientCertificateExpiresAt(), checkedAt,
                status, findings, recommendations);
        status = applyCertificateFinding("cluster CA certificate", lifetime.caCertificateExpiresAt(), checkedAt,
                status, findings, recommendations);
        if (ageDays >= 90) {
            if ("HEALTHY".equals(status)) status = "WARNING";
            findings.add("The stored credential has not been replaced for " + ageDays + " days.");
            recommendations.add("Confirm the credential rotation policy and replace long-lived credentials.");
        }
        if (findings.isEmpty()) {
            findings.add("Connection verification and observable credential lifetime checks passed.");
        }
        Instant observableExpiry = earliest(expiresAt, lifetime.clientCertificateExpiresAt(), lifetime.caCertificateExpiresAt());
        Instant recommendedRotationBy = observableExpiry == null
                ? encrypted.createdAt().plus(90, ChronoUnit.DAYS)
                : observableExpiry.minus(14, ChronoUnit.DAYS);
        String rotationStatus = recommendedRotationBy.isBefore(checkedAt) ? "DUE"
                : recommendedRotationBy.isBefore(checkedAt.plus(30, ChronoUnit.DAYS)) ? "SCHEDULE" : "CURRENT";
        List<String> rotationSteps = List.of(
                "Create a replacement credential without deleting the active credential.",
                "Use connection verification and Capability Matrix with the replacement.",
                "Activate the replacement, run one resource sync, and verify analysis access.",
                "Revoke the previous credential only after the verification window completes."
        );
        return new ClusterReadinessReport.CredentialHealth(status, encrypted.credentialType().name(),
                connection.reachable(), connection.kubernetesVersion(), encrypted.createdAt(), ageDays, expiresAt,
                expiresInDays, lifetime.clientCertificateExpiresAt(), lifetime.caCertificateExpiresAt(),
                encrypted.algorithm(), encrypted.keyId(), false, rotationStatus, recommendedRotationBy, rotationSteps,
                List.copyOf(findings),
                List.copyOf(recommendations), connection.message());
    }

    /** ClusterReadinessService의 applyCertificateFinding 처리에 필요한 업무 로직을 수행한다. */
    private String applyCertificateFinding(String label, Instant expiry, Instant checkedAt, String status,
                                           List<String> findings, List<String> recommendations) {
        if (expiry == null) return status;
        long days = ChronoUnit.DAYS.between(checkedAt, expiry);
        if (days < 0) {
            findings.add("The " + label + " is expired.");
            recommendations.add("Replace the " + label + " before using this credential.");
            return "CRITICAL";
        }
        if (days <= 14) {
            findings.add("The " + label + " expires within 14 days.");
            recommendations.add("Rotate the " + label + " immediately.");
            return "CRITICAL";
        }
        if (days <= 30) {
            findings.add("The " + label + " expires within 30 days.");
            recommendations.add("Schedule " + label + " rotation.");
            return "CRITICAL".equals(status) ? status : "WARNING";
        }
        return status;
    }

    /** ClusterReadinessService의 assessUpgrade 처리에 필요한 업무 로직을 수행한다. */
    private ClusterReadinessReport.UpgradeReadiness assessUpgrade(UUID clusterId,
                                                                   KubernetesConnectionTestResult connection,
                                                                   List<KubernetesNode> nodes,
                                                                   String targetVersion) {
        String currentVersion = normalizeVersion(connection.kubernetesVersion());
        String effectiveTarget = targetVersion == null ? nextMinorVersion(currentVersion) : targetVersion;
        int controlPlaneMinor = minor(currentVersion);
        int targetMinor = minor(effectiveTarget);
        List<ClusterReadinessReport.UpgradeFinding> findings = new ArrayList<>();
        List<ClusterReadinessReport.NodeVersion> nodeVersions = nodes.stream().map(node -> {
            int skew = controlPlaneMinor < 0 || minor(node.kubernetesVersion()) < 0
                    ? 0 : controlPlaneMinor - minor(node.kubernetesVersion());
            if (!"Ready".equalsIgnoreCase(node.status())) {
                findings.add(new ClusterReadinessReport.UpgradeFinding("HIGH", "NODE_HEALTH",
                        "Node is not Ready", "Resolve node health before upgrading.", "Node/" + node.name(),
                        "status=" + node.status()));
            }
            if (skew > 2) {
                findings.add(new ClusterReadinessReport.UpgradeFinding("HIGH", "VERSION_SKEW",
                        "Kubelet version skew is too large", "Upgrade this node before advancing the control plane.",
                        "Node/" + node.name(), "kubelet=" + node.kubernetesVersion() + ", controlPlane=" + currentVersion));
            }
            return new ClusterReadinessReport.NodeVersion(node.name(), node.status(), node.kubernetesVersion(), skew);
        }).toList();

        if (!connection.reachable()) {
            findings.add(new ClusterReadinessReport.UpgradeFinding("CRITICAL", "CONNECTIVITY",
                    "Cluster version cannot be verified", connection.message(), null, "connection-test"));
        }
        if (controlPlaneMinor >= 0 && targetMinor >= 0 && targetMinor - controlPlaneMinor > 1) {
            findings.add(new ClusterReadinessReport.UpgradeFinding("HIGH", "UPGRADE_PATH",
                    "Target skips Kubernetes minor versions", "Upgrade one minor version at a time.", null,
                    currentVersion + " -> " + effectiveTarget));
        }
        if (controlPlaneMinor >= 0 && targetMinor >= 0 && targetMinor < controlPlaneMinor) {
            findings.add(new ClusterReadinessReport.UpgradeFinding("HIGH", "TARGET_VERSION",
                    "Target version is older than the running control plane",
                    "Choose the next supported Kubernetes minor version.", null,
                    currentVersion + " -> " + effectiveTarget));
        }
        appendDeprecatedApiFindings(clusterId, targetMinor, findings);
        int high = (int) findings.stream().filter(item -> "HIGH".equals(item.severity()) || "CRITICAL".equals(item.severity())).count();
        int medium = (int) findings.stream().filter(item -> "MEDIUM".equals(item.severity())).count();
        int score = Math.max(0, 100 - (high * 30) - (medium * 12));
        String status = !connection.reachable() ? "UNKNOWN" : high > 0 ? "BLOCKED" : medium > 0 ? "REVIEW" : "READY";
        List<String> steps = List.of(
                "Back up control-plane state and verify restore procedures.",
                "Resolve blocking findings and validate deprecated APIs against the target minor version.",
                "Upgrade the control plane one minor at a time, then upgrade worker nodes.",
                "Run workload smoke tests, event review, and rollback verification after each step."
        );
        return new ClusterReadinessReport.UpgradeReadiness(status, score, currentVersion, effectiveTarget,
                nodeVersions, List.copyOf(findings), steps, UPGRADE_CATALOG.version(),
                "KUBERNETES_VERSION_NODE_CRD_AND_SNAPSHOT_FACTS");
    }

    /** ClusterReadinessService의 appendDeprecatedApiFindings 처리에 필요한 업무 로직을 수행한다. */
    private void appendDeprecatedApiFindings(UUID clusterId, int targetMinor,
                                             List<ClusterReadinessReport.UpgradeFinding> findings) {
        if (targetMinor < 0) return;
        for (KubernetesResourceSnapshot resource : clusterSnapshotUseCase.listResources(clusterId, null, null)) {
            String apiVersion = apiVersion(resource.rawJson());
            KubernetesUpgradeCatalog.RemovedApiRule removed = UPGRADE_CATALOG.removedApis().stream()
                    .filter(rule -> rule.apiVersion().equals(apiVersion)).findFirst().orElse(null);
            if (removed != null && targetMinor >= removed.removedInMinor()) {
                findings.add(new ClusterReadinessReport.UpgradeFinding("HIGH", "DEPRECATED_API",
                        "Removed API version detected", "Migrate the manifest to " + removed.replacement()
                        + " before upgrading to the target version.", resource.resourceType() + "/"
                        + resource.resourceName(), "apiVersion=" + apiVersion));
            }
            if ("CustomResourceDefinition".equalsIgnoreCase(resource.resourceType())) {
                appendCrdFindings(resource, findings);
            }
            appendAddonFinding(resource, findings);
        }
    }

    /** ClusterReadinessService의 appendAddonFinding 처리에 필요한 업무 로직을 수행한다. */
    private void appendAddonFinding(KubernetesResourceSnapshot resource,
                                    List<ClusterReadinessReport.UpgradeFinding> findings) {
        KubernetesUpgradeCatalog.AddonRule rule = UPGRADE_CATALOG.addons().stream()
                .filter(item -> item.kind().equalsIgnoreCase(resource.resourceType()))
                .findFirst().orElse(null);
        if (rule == null) return;
        if ("DaemonSet".equalsIgnoreCase(resource.resourceType())) {
            String name = resource.resourceName().toLowerCase(Locale.ROOT);
            if (List.of("cni", "calico", "cilium", "flannel", "weave").stream().noneMatch(name::contains)) return;
        }
        String reference = resource.resourceType() + "/" + resource.resourceName();
        boolean duplicate = findings.stream().anyMatch(item -> "ADDON_COMPATIBILITY".equals(item.category())
                && reference.equals(item.resourceRef()));
        if (!duplicate) {
            findings.add(new ClusterReadinessReport.UpgradeFinding("MEDIUM", "ADDON_COMPATIBILITY",
                    rule.category() + " compatibility requires vendor verification", rule.guidance(),
                    reference, "catalog=" + UPGRADE_CATALOG.version()));
        }
    }

    /** ClusterReadinessService의 earliest 처리에 필요한 업무 로직을 수행한다. */
    private Instant earliest(Instant... values) {
        Instant result = null;
        for (Instant value : values) {
            if (value != null && (result == null || value.isBefore(result))) result = value;
        }
        return result;
    }

    /** ClusterReadinessService의 appendCrdFindings 처리에 필요한 업무 로직을 수행한다. */
    private void appendCrdFindings(KubernetesResourceSnapshot resource,
                                   List<ClusterReadinessReport.UpgradeFinding> findings) {
        try {
            JsonNode root = objectMapper.readTree(resource.rawJson());
            JsonNode storedVersions = root.path("status").path("storedVersions");
            JsonNode declaredVersions = root.path("spec").path("versions");
            List<String> served = new ArrayList<>();
            if (declaredVersions.isArray()) {
                declaredVersions.forEach(version -> {
                    if (version.path("served").asBoolean(false)) served.add(version.path("name").asText());
                });
            }
            if (storedVersions.isArray()) {
                storedVersions.forEach(version -> {
                    if (!served.contains(version.asText())) {
                        findings.add(new ClusterReadinessReport.UpgradeFinding("HIGH", "CRD_STORED_VERSION",
                                "CRD stores an unserved version",
                                "Migrate custom resources and remove the obsolete storedVersion before upgrade.",
                                "CustomResourceDefinition/" + resource.resourceName(),
                                "storedVersion=" + version.asText() + ", servedVersions=" + served));
                    }
                });
            }
            JsonNode conditions = root.path("status").path("conditions");
            if (conditions.isArray()) {
                conditions.forEach(condition -> {
                    String type = condition.path("type").asText();
                    if (("Established".equals(type) || "NamesAccepted".equals(type))
                            && "False".equalsIgnoreCase(condition.path("status").asText())) {
                        findings.add(new ClusterReadinessReport.UpgradeFinding("HIGH", "CRD_CONDITION",
                                "CRD is not ready for upgrade", condition.path("message").asText("Resolve the CRD condition."),
                                "CustomResourceDefinition/" + resource.resourceName(),
                                type + "=False, reason=" + condition.path("reason").asText("unknown")));
                    }
                });
            }
        } catch (RuntimeException | java.io.IOException ignored) {
            findings.add(new ClusterReadinessReport.UpgradeFinding("MEDIUM", "CRD_EVIDENCE",
                    "CRD compatibility could not be parsed",
                    "Inspect the CRD storedVersions and conditions manually.",
                    "CustomResourceDefinition/" + resource.resourceName(), "raw snapshot parse failed"));
        }
    }

    /** ClusterReadinessService의 credentialLifetime 처리에 필요한 업무 로직을 수행한다. */
    private CredentialLifetime credentialLifetime(ClusterCredentialType type, String payload) {
        try {
            String token;
            Instant clientCertificateExpiry = null;
            Instant caCertificateExpiry = null;
            if (type == ClusterCredentialType.KUBECONFIG) {
                Config config = Config.fromKubeconfig(payload);
                token = config.getOauthToken();
                clientCertificateExpiry = certificateExpiry(config.getClientCertData());
                caCertificateExpiry = certificateExpiry(config.getCaCertData());
            } else {
                JsonNode source = objectMapper.readTree(payload);
                token = source.path("token").asText(null);
                caCertificateExpiry = certificateExpiry(source.path("caCertificate").asText(null));
            }
            if (token == null || token.isBlank()) {
                return new CredentialLifetime(null, clientCertificateExpiry, caCertificateExpiry);
            }
            String[] parts = token.split("\\.");
            if (parts.length < 2) return new CredentialLifetime(null, clientCertificateExpiry, caCertificateExpiry);
            JsonNode claims = objectMapper.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            long exp = claims.path("exp").asLong(0);
            return new CredentialLifetime(exp <= 0 ? null : Instant.ofEpochSecond(exp),
                    clientCertificateExpiry, caCertificateExpiry);
        } catch (RuntimeException | java.io.IOException exception) {
            return new CredentialLifetime(null, null, null);
        }
    }

    /** ClusterReadinessService의 certificateExpiry 처리에 필요한 업무 로직을 수행한다. */
    private Instant certificateExpiry(String encodedCertificate) {
        if (encodedCertificate == null || encodedCertificate.isBlank()) return null;
        try {
            byte[] bytes = encodedCertificate.contains("BEGIN CERTIFICATE")
                    ? encodedCertificate.getBytes(StandardCharsets.US_ASCII)
                    : Base64.getMimeDecoder().decode(encodedCertificate);
            X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(bytes));
            return certificate.getNotAfter().toInstant();
        } catch (Exception ignored) {
            return null;
        }
    }

    /** ClusterReadinessService의 rootMessage 처리에 필요한 업무 로직을 수행한다. */
    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    /** ClusterReadinessService의 apiVersion 처리에 필요한 업무 로직을 수행한다. */
    private String apiVersion(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return "";
        try {
            return objectMapper.readTree(rawJson).path("apiVersion").asText("");
        } catch (java.io.IOException exception) {
            return "";
        }
    }

    /** ClusterReadinessService의 safeNodes 처리에 필요한 업무 로직을 수행한다. */
    private List<KubernetesNode> safeNodes(KubernetesConnectionCredential credential) {
        try {
            return kubernetesClusterPort.listNodes(credential);
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    /** ClusterReadinessService의 capabilitySpecs 처리에 필요한 업무 로직을 수행한다. */
    private List<CapabilitySpec> capabilitySpecs() {
        return List.of(
                new CapabilitySpec("namespace-read", "DISCOVERY", "Read namespaces", "list", "", "namespaces", null, false),
                new CapabilitySpec("workload-read", "OBSERVE", "Read workloads", "list", "apps", "deployments", null, true),
                new CapabilitySpec("pod-read", "OBSERVE", "Read pods", "list", "", "pods", null, true),
                new CapabilitySpec("event-read", "OBSERVE", "Read events", "list", "", "events", null, true),
                new CapabilitySpec("log-read", "DIAGNOSE", "Read pod logs", "get", "", "pods", "log", true),
                new CapabilitySpec("deployment-patch", "REMEDIATE", "Patch deployments", "patch", "apps", "deployments", null, true),
                new CapabilitySpec("deployment-update", "REMEDIATE", "Update deployments", "update", "apps", "deployments", null, true),
                new CapabilitySpec("pod-delete", "DISRUPTIVE", "Delete pods", "delete", "", "pods", null, true),
                new CapabilitySpec("secret-read", "SENSITIVE", "Read secrets", "get", "", "secrets", null, true)
        );
    }

    /** ClusterReadinessService의 credentialScore 처리에 필요한 업무 로직을 수행한다. */
    private int credentialScore(String status) {
        return switch (status) {
            case "HEALTHY" -> 100;
            case "WARNING" -> 65;
            default -> 20;
        };
    }

    /** ClusterReadinessService의 statusForScore 처리에 필요한 업무 로직을 수행한다. */
    private String statusForScore(int score, boolean reachable) {
        if (!reachable) return "CRITICAL";
        if (score >= 85) return "READY";
        if (score >= 60) return "REVIEW";
        return "BLOCKED";
    }

    /** ClusterReadinessService의 minor 처리에 필요한 업무 로직을 수행한다. */
    private int minor(String version) {
        if (version == null) return -1;
        String[] parts = version.replaceFirst("^v", "").split("\\.");
        if (parts.length < 2) return -1;
        try {
            return Integer.parseInt(parts[1].replaceAll("[^0-9].*$", ""));
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    /** ClusterReadinessService의 normalizeVersion 처리 데이터를 필요한 표현으로 변환한다. */
    private String normalizeVersion(String version) {
        if (version == null || version.isBlank()) return null;
        String normalized = version.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("v") ? normalized : "v" + normalized;
    }

    /** ClusterReadinessService의 nextMinorVersion 처리에 필요한 업무 로직을 수행한다. */
    private String nextMinorVersion(String currentVersion) {
        if (currentVersion == null) return null;
        String normalized = normalizeVersion(currentVersion);
        String[] parts = normalized.replaceFirst("^v", "").split("\\.");
        if (parts.length < 2) return normalized;
        try {
            return "v" + Integer.parseInt(parts[0]) + "." + (minor(normalized) + 1);
        } catch (NumberFormatException exception) {
            return normalized;
        }
    }

    /** ClusterReadinessService의 textOrDefault 처리에 필요한 업무 로직을 수행한다. */
    private String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record CapabilitySpec(String id, String category, String displayName, String verb, String group,
                                  String resource, String subresource, boolean namespaceScoped) {
    }

    private record CacheKey(UUID clusterId, String namespace, String targetVersion) {
    }

    private record CacheEntry(ClusterReadinessReport report, Instant expiresAt) {
    }

    private record CredentialLifetime(Instant tokenExpiresAt, Instant clientCertificateExpiresAt,
                                      Instant caCertificateExpiresAt) {
    }
}
