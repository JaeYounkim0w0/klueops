package io.strato.aiops.application.service;

import io.strato.aiops.application.port.in.GetClusterRuntimeUseCase;
import io.strato.aiops.application.port.in.GetClusterUseCase;
import io.strato.aiops.application.port.in.GetClusterCredentialUseCase;
import io.strato.aiops.application.port.in.DeleteClusterUseCase;
import io.strato.aiops.application.port.in.ClusterCredentialResult;
import io.strato.aiops.application.port.in.KubernetesNamespaceSummary;
import io.strato.aiops.application.port.in.KubernetesNodeSummary;
import io.strato.aiops.application.port.in.KubernetesResourceManifestResult;
import io.strato.aiops.application.port.in.RegisterClusterCommand;
import io.strato.aiops.application.port.in.RegisterClusterUseCase;
import io.strato.aiops.application.port.in.ClusterConnectionTestResult;
import io.strato.aiops.application.port.in.TestClusterConnectionUseCase;
import io.strato.aiops.application.port.out.AuditLogRepositoryPort;
import io.strato.aiops.application.port.out.ClusterDataDeletionPort;
import io.strato.aiops.application.port.out.ClusterCredentialRepositoryPort;
import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.application.port.out.ClusterSyncSettingRepositoryPort;
import io.strato.aiops.application.port.out.KubernetesClusterPort;
import io.strato.aiops.application.port.out.KubernetesConnectionCredential;
import io.strato.aiops.application.port.out.KubernetesConnectionTestResult;
import io.strato.aiops.application.port.out.KubernetesResourceManifest;
import io.strato.aiops.application.port.out.KubernetesResourceManifestPort;
import io.strato.aiops.application.port.out.KubernetesResourceSnapshotRepositoryPort;
import io.strato.aiops.application.port.out.SecretCryptoPort;
import io.strato.aiops.application.port.out.TenantRepositoryPort;
import io.strato.aiops.application.port.out.WorkspaceRepositoryPort;
import io.strato.aiops.domain.audit.AuditLog;
import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import io.strato.aiops.domain.cluster.ClusterSyncSetting;
import io.strato.aiops.domain.cluster.EncryptedClusterCredential;
import io.strato.aiops.domain.cluster.EncryptedSecret;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;
import io.strato.aiops.domain.tenancy.TenancyDefaults;
import io.strato.aiops.domain.tenancy.TenantStatus;
import io.strato.aiops.domain.tenancy.Workspace;
import io.strato.aiops.domain.tenancy.WorkspaceStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

@Service
public class ClusterApplicationService implements RegisterClusterUseCase, TestClusterConnectionUseCase, GetClusterUseCase,
        GetClusterRuntimeUseCase, DeleteClusterUseCase, GetClusterCredentialUseCase {

    private final ClusterRepositoryPort clusterRepositoryPort;
    private final ClusterDataDeletionPort clusterDataDeletionPort;
    private final ClusterCredentialRepositoryPort clusterCredentialRepositoryPort;
    private final ClusterSyncSettingRepositoryPort clusterSyncSettingRepositoryPort;
    private final KubernetesClusterPort kubernetesClusterPort;
    private final KubernetesResourceManifestPort kubernetesResourceManifestPort;
    private final KubernetesResourceSnapshotRepositoryPort resourceSnapshotRepositoryPort;
    private final SecretCryptoPort secretCryptoPort;
    private final AuditLogRepositoryPort auditLogRepositoryPort;
    private final TenantRepositoryPort tenantRepositoryPort;
    private final WorkspaceRepositoryPort workspaceRepositoryPort;

    public ClusterApplicationService(
            ClusterRepositoryPort clusterRepositoryPort,
            ClusterDataDeletionPort clusterDataDeletionPort,
            ClusterCredentialRepositoryPort clusterCredentialRepositoryPort,
            ClusterSyncSettingRepositoryPort clusterSyncSettingRepositoryPort,
            KubernetesClusterPort kubernetesClusterPort,
            KubernetesResourceManifestPort kubernetesResourceManifestPort,
            KubernetesResourceSnapshotRepositoryPort resourceSnapshotRepositoryPort,
            SecretCryptoPort secretCryptoPort,
            AuditLogRepositoryPort auditLogRepositoryPort,
            TenantRepositoryPort tenantRepositoryPort,
            WorkspaceRepositoryPort workspaceRepositoryPort
    ) {
        this.clusterRepositoryPort = clusterRepositoryPort;
        this.clusterDataDeletionPort = clusterDataDeletionPort;
        this.clusterCredentialRepositoryPort = clusterCredentialRepositoryPort;
        this.clusterSyncSettingRepositoryPort = clusterSyncSettingRepositoryPort;
        this.kubernetesClusterPort = kubernetesClusterPort;
        this.kubernetesResourceManifestPort = kubernetesResourceManifestPort;
        this.resourceSnapshotRepositoryPort = resourceSnapshotRepositoryPort;
        this.secretCryptoPort = secretCryptoPort;
        this.auditLogRepositoryPort = auditLogRepositoryPort;
        this.tenantRepositoryPort = tenantRepositoryPort;
        this.workspaceRepositoryPort = workspaceRepositoryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Cluster> listClusters() {
        return clusterRepositoryPort.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Cluster> listClusters(UUID tenantId, UUID workspaceId) {
        if (workspaceId != null && tenantId == null) {
            throw new IllegalArgumentException("tenantId is required when workspaceId is provided");
        }
        return clusterRepositoryPort.findAll(tenantId, workspaceId);
    }

    @Override
    @Transactional(readOnly = true)
    public Cluster getCluster(UUID clusterId) {
        return findCluster(clusterId);
    }

    @Override
    @Transactional(readOnly = true)
    public ClusterCredentialResult getClusterCredential(UUID clusterId, boolean reveal, String actor, String requestId) {
        Cluster cluster = findCluster(clusterId);
        EncryptedClusterCredential credential = clusterCredentialRepositoryPort.findByClusterId(cluster.id())
                .orElseThrow(() -> new NoSuchElementException("Cluster credential not found: " + clusterId));
        String payload = secretCryptoPort.decrypt(new EncryptedSecret(
                credential.encryptedPayload(),
                credential.keyId(),
                credential.algorithm(),
                credential.nonce()
        ));
        if (reveal) {
            auditLogRepositoryPort.save(AuditLog.create(
                    "CLUSTER_CREDENTIAL_REVEALED",
                    "CLUSTER",
                    cluster.id().toString(),
                    actor,
                    requestId
            ));
        }
        return new ClusterCredentialResult(
                cluster.id(),
                credential.credentialType(),
                reveal ? payload : maskCredentialPayload(payload),
                reveal,
                !reveal,
                credential.createdAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<KubernetesNamespaceSummary> listNamespaces(UUID clusterId) {
        Cluster cluster = findCluster(clusterId);
        return kubernetesClusterPort.listNamespaces(connectionCredential(cluster.id())).stream()
                .map(namespace -> new KubernetesNamespaceSummary(namespace.name(), namespace.status()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<KubernetesNodeSummary> listNodes(UUID clusterId) {
        Cluster cluster = findCluster(clusterId);
        return kubernetesClusterPort.listNodes(connectionCredential(cluster.id())).stream()
                .map(node -> new KubernetesNodeSummary(
                        node.name(),
                        node.status(),
                        node.kubernetesVersion(),
                        node.osImage(),
                        node.containerRuntimeVersion()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public KubernetesResourceManifestResult getResourceManifest(UUID clusterId, String namespace, String resourceType, String resourceName) {
        Cluster cluster = findCluster(clusterId);
        try {
            KubernetesResourceManifest manifest = kubernetesResourceManifestPort.getResourceManifest(
                    connectionCredential(cluster.id()),
                    namespace,
                    resourceType,
                    resourceName
            );
            return new KubernetesResourceManifestResult(
                    cluster.id(),
                    manifest.namespace(),
                    manifest.resourceType(),
                    manifest.resourceName(),
                    manifest.manifestYaml(),
                    manifest.secretRedacted(),
                    manifest.collectedAt(),
                    "LIVE",
                    null
            );
        } catch (RuntimeException exception) {
            return fallbackResourceManifest(cluster.id(), namespace, resourceType, resourceName, exception);
        }
    }

    @Override
    @Transactional
    public Cluster registerCluster(RegisterClusterCommand command, String actor, String requestId) {
        validate(command);

        UUID tenantId = command.tenantId() == null ? TenancyDefaults.TENANT_ID : command.tenantId();
        UUID workspaceId = command.workspaceId() == null ? TenancyDefaults.WORKSPACE_ID : command.workspaceId();
        validatePlacement(tenantId, workspaceId);

        Cluster cluster = Cluster.register(
                tenantId,
                workspaceId,
                command.name(),
                command.description(),
                command.environment(),
                command.provider(),
                command.region(),
                actor
        );
        Cluster savedCluster = clusterRepositoryPort.save(cluster);

        String credentialPayload = credentialPayload(command);
        EncryptedSecret encryptedSecret = secretCryptoPort.encrypt(credentialPayload);
        clusterCredentialRepositoryPort.save(EncryptedClusterCredential.create(
                savedCluster.id(),
                command.credentialType(),
                encryptedSecret
        ));

        RegisterClusterCommand.SyncSettings syncSettings = command.syncSettings();
        clusterSyncSettingRepositoryPort.save(ClusterSyncSetting.create(
                savedCluster.id(),
                syncSettings == null ? null : syncSettings.autoSyncEnabled(),
                syncSettings == null ? null : syncSettings.syncIntervalSeconds()
        ));

        auditLogRepositoryPort.save(AuditLog.create(
                "CLUSTER_REGISTERED",
                "CLUSTER",
                savedCluster.id().toString(),
                actor,
                requestId
        ));

        return savedCluster;
    }

    @Override
    @Transactional
    public ClusterConnectionTestResult testClusterConnection(UUID clusterId, String actor, String requestId) {
        Cluster cluster = findCluster(clusterId);

        KubernetesConnectionTestResult result = kubernetesClusterPort.testConnection(connectionCredential(cluster.id()));

        auditLogRepositoryPort.save(AuditLog.create(
                "CLUSTER_CONNECTION_TESTED",
                "CLUSTER",
                cluster.id().toString(),
                actor,
                requestId
        ));

        return new ClusterConnectionTestResult(
                cluster.id(),
                result.reachable(),
                result.kubernetesVersion(),
                result.namespaces(),
                result.message(),
                result.checkedAt()
        );
    }

    @Override
    @Transactional
    public void deleteCluster(UUID clusterId, String actor, String requestId) {
        Cluster cluster = findCluster(clusterId);

        auditLogRepositoryPort.save(AuditLog.create(
                "CLUSTER_DELETED",
                "CLUSTER",
                cluster.id().toString(),
                actor,
                requestId
        ));

        clusterDataDeletionPort.deleteClusterData(cluster.id());
    }

    private Cluster findCluster(UUID clusterId) {
        return clusterRepositoryPort.findById(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster not found: " + clusterId));
    }

    private KubernetesConnectionCredential connectionCredential(UUID clusterId) {
        EncryptedClusterCredential credential = clusterCredentialRepositoryPort.findByClusterId(clusterId)
                .orElseThrow(() -> new NoSuchElementException("Cluster credential not found: " + clusterId));

        String plaintextPayload = secretCryptoPort.decrypt(new EncryptedSecret(
                credential.encryptedPayload(),
                credential.keyId(),
                credential.algorithm(),
                credential.nonce()
        ));
        return new KubernetesConnectionCredential(credential.credentialType(), plaintextPayload);
    }

    private void validate(RegisterClusterCommand command) {
        if (command.credentialType() == ClusterCredentialType.KUBECONFIG) {
            if (command.kubeconfig() == null || command.kubeconfig().isBlank()) {
                throw new IllegalArgumentException("kubeconfig is required when credentialType is KUBECONFIG");
            }
            if (command.kubeconfig().contains("exec:")) {
                throw new IllegalArgumentException("exec plugin kubeconfig is not supported in MVP. Use ServiceAccount token credential instead.");
            }
            return;
        }

        if (command.credentialType() == ClusterCredentialType.SERVICE_ACCOUNT_TOKEN && command.serviceAccount() == null) {
            throw new IllegalArgumentException("serviceAccount credential is required when credentialType is SERVICE_ACCOUNT_TOKEN");
        }
    }

    private void validatePlacement(UUID tenantId, UUID workspaceId) {
        var tenant = tenantRepositoryPort.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
        Workspace workspace = workspaceRepositoryPort.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));
        if (!workspace.tenantId().equals(tenant.id())) {
            throw new IllegalArgumentException("Workspace does not belong to the selected tenant");
        }
        if (tenant.status() != TenantStatus.ACTIVE || workspace.status() != WorkspaceStatus.ACTIVE) {
            throw new IllegalArgumentException("Cluster cannot be registered in an inactive tenant or workspace");
        }
    }

    private String credentialPayload(RegisterClusterCommand command) {
        if (command.credentialType() == ClusterCredentialType.KUBECONFIG) {
            return command.kubeconfig();
        }

        RegisterClusterCommand.ServiceAccountCredential serviceAccount = command.serviceAccount();
        return """
                {
                  "apiServerUrl": "%s",
                  "caCertificate": "%s",
                  "token": "%s"
                }
                """.formatted(
                escapeJson(serviceAccount.apiServerUrl()),
                escapeJson(serviceAccount.caCertificate()),
                escapeJson(serviceAccount.token())
        );
    }

    private String escapeJson(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String maskCredentialPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return "";
        }
        return payload
                .replaceAll("(?im)^([\\s-]*token:\\s*).+$", "$1***")
                .replaceAll("(?im)^([\\s-]*client-certificate-data:\\s*).+$", "$1***")
                .replaceAll("(?im)^([\\s-]*client-key-data:\\s*).+$", "$1***")
                .replaceAll("(?im)^([\\s-]*certificate-authority-data:\\s*).+$", "$1***")
                .replaceAll("(?i)(\"token\"\\s*:\\s*\").*?(\")", "$1***$2")
                .replaceAll("(?i)(\"caCertificate\"\\s*:\\s*\").*?(\")", "$1***$2");
    }

    private KubernetesResourceManifestResult fallbackResourceManifest(
            UUID clusterId,
            String namespace,
            String resourceType,
            String resourceName,
            RuntimeException exception
    ) {
        return resourceSnapshotRepositoryPort.findLatest(clusterId, namespace, resourceType, 200)
                .stream()
                .filter(snapshot -> Objects.equals(snapshot.resourceName(), resourceName))
                .findFirst()
                .map(snapshot -> new KubernetesResourceManifestResult(
                        clusterId,
                        snapshot.namespace(),
                        snapshot.resourceType(),
                        snapshot.resourceName(),
                        fallbackManifestBody(snapshot),
                        "Secret".equalsIgnoreCase(snapshot.resourceType()),
                        snapshot.collectedAt(),
                        "SNAPSHOT_FALLBACK",
                        conciseExceptionMessage(exception)
                ))
                .orElseThrow(() -> exception);
    }

    private String fallbackManifestBody(KubernetesResourceSnapshot snapshot) {
        if ("Secret".equalsIgnoreCase(snapshot.resourceType())) {
            return """
                    # Live Kubernetes API lookup failed.
                    # Secret raw snapshot is intentionally hidden. Showing sanitized summary only.
                    %s
                    """.formatted(snapshot.summaryJson());
        }
        String rawJson = snapshot.rawJson();
        if (rawJson != null && !rawJson.isBlank()) {
            return """
                    # Live Kubernetes API lookup failed.
                    # Showing the latest synchronized snapshot JSON instead.
                    %s
                    """.formatted(rawJson);
        }
        return """
                # Live Kubernetes API lookup failed.
                # Showing the latest synchronized summary JSON instead.
                %s
                """.formatted(snapshot.summaryJson());
    }

    private String conciseExceptionMessage(RuntimeException exception) {
        Throwable root = exception;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage() == null || root.getMessage().isBlank()
                ? exception.getMessage()
                : root.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) + "..." : message;
    }
}
