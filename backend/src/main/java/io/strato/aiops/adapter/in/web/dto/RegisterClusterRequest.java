package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.RegisterClusterCommand;
import io.strato.aiops.domain.cluster.ClusterCredentialType;
import io.strato.aiops.domain.cluster.ClusterEnvironment;
import io.strato.aiops.domain.cluster.ClusterProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

@Schema(description = "Cluster registration request. KUBECONFIG is preferred. Use SERVICE_ACCOUNT_TOKEN when kubeconfig is not available or not supported.")
public record RegisterClusterRequest(
        @Schema(description = "Owning tenant ID")
        UUID tenantId,

        @Schema(description = "Owning workspace ID")
        UUID workspaceId,

        @Schema(description = "Cluster display name", example = "dev-cluster")
        @NotBlank
        String name,

        @Schema(description = "Cluster description", example = "Development Kubernetes cluster")
        String description,

        @Schema(description = "Cluster environment")
        @NotNull
        ClusterEnvironment environment,

        @Schema(description = "Kubernetes provider")
        @NotNull
        ClusterProvider provider,

        @Schema(description = "Region or location", example = "ap-northeast-2")
        String region,

        @Schema(description = "Credential type. Prefer KUBECONFIG. Use SERVICE_ACCOUNT_TOKEN as fallback.")
        @NotNull
        ClusterCredentialType credentialType,

        @Schema(description = "kubeconfig YAML content. Required when credentialType is KUBECONFIG.")
        String kubeconfig,

        @Schema(description = "ServiceAccount token credential. Required when credentialType is SERVICE_ACCOUNT_TOKEN.")
        @Valid
        ServiceAccountCredentialRequest serviceAccount,

        @Schema(description = "Namespace access settings")
        @Valid
        NamespaceAccessRequest namespaceAccess,

        @Schema(description = "Synchronization settings")
        @Valid
        SyncSettingsRequest syncSettings
) {
    public RegisterClusterRequest(String name, String description, ClusterEnvironment environment,
                                  ClusterProvider provider, String region, ClusterCredentialType credentialType,
                                  String kubeconfig, ServiceAccountCredentialRequest serviceAccount,
                                  NamespaceAccessRequest namespaceAccess, SyncSettingsRequest syncSettings) {
        this(null, null, name, description, environment, provider, region, credentialType, kubeconfig,
                serviceAccount, namespaceAccess, syncSettings);
    }

    public RegisterClusterCommand toCommand() {
        return new RegisterClusterCommand(
                tenantId,
                workspaceId,
                name,
                description,
                environment,
                provider,
                region,
                credentialType,
                kubeconfig,
                serviceAccount == null ? null : new RegisterClusterCommand.ServiceAccountCredential(
                        serviceAccount.apiServerUrl(),
                        serviceAccount.caCertificate(),
                        serviceAccount.token()
                ),
                namespaceAccess == null ? null : new RegisterClusterCommand.NamespaceAccess(
                        namespaceAccess.clusterWide(),
                        namespaceAccess.allowedNamespaces(),
                        namespaceAccess.defaultNamespace()
                ),
                syncSettings == null ? null : new RegisterClusterCommand.SyncSettings(
                        syncSettings.autoSyncEnabled(),
                        syncSettings.syncIntervalSeconds()
                )
        );
    }

    @Schema(description = "ServiceAccount token based cluster credential")
    public record ServiceAccountCredentialRequest(
            @Schema(description = "Kubernetes API server URL", example = "https://10.0.0.1:6443")
            @NotBlank
            String apiServerUrl,

            @Schema(description = "PEM encoded CA certificate")
            @NotBlank
            String caCertificate,

            @Schema(description = "ServiceAccount bearer token")
            @NotBlank
            String token
    ) {
    }

    @Schema(description = "Namespace access settings")
    public record NamespaceAccessRequest(
            @Schema(description = "Whether cluster-wide access is allowed")
            boolean clusterWide,

            @Schema(description = "Allowed namespaces when clusterWide is false")
            List<String> allowedNamespaces,

            @Schema(description = "Default namespace", example = "default")
            String defaultNamespace
    ) {
    }

    @Schema(description = "Cluster synchronization settings")
    public record SyncSettingsRequest(
            @Schema(description = "Enable automatic sync", defaultValue = "true")
            Boolean autoSyncEnabled,

            @Schema(description = "Sync interval seconds. Default is 300 seconds.", defaultValue = "300")
            @Positive
            Integer syncIntervalSeconds
    ) {
    }
}
