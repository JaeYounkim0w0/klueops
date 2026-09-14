package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.cluster.ClusterCredentialType;
import io.strato.aiops.domain.cluster.ClusterEnvironment;
import io.strato.aiops.domain.cluster.ClusterProvider;

import java.util.List;
import java.util.UUID;

public record RegisterClusterCommand(
        UUID tenantId,
        UUID workspaceId,
        String name,
        String description,
        ClusterEnvironment environment,
        ClusterProvider provider,
        String region,
        ClusterCredentialType credentialType,
        String kubeconfig,
        ServiceAccountCredential serviceAccount,
        NamespaceAccess namespaceAccess,
        SyncSettings syncSettings
) {
    public record ServiceAccountCredential(
            String apiServerUrl,
            String caCertificate,
            String token
    ) {
    }

    public record NamespaceAccess(
            boolean clusterWide,
            List<String> allowedNamespaces,
            String defaultNamespace
    ) {
    }

    public record SyncSettings(
            Boolean autoSyncEnabled,
            Integer syncIntervalSeconds
    ) {
    }
}
