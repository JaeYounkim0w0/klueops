package io.strato.aiops.adapter.in.web;

import io.strato.aiops.adapter.in.web.dto.ClusterConnectionTestResponse;
import io.strato.aiops.adapter.in.web.dto.ClusterResourcePageResponse;
import io.strato.aiops.adapter.in.web.dto.ClusterCredentialResponse;
import io.strato.aiops.adapter.in.web.dto.ClusterResponse;
import io.strato.aiops.adapter.in.web.dto.ClusterSyncSettingsResponse;
import io.strato.aiops.adapter.in.web.dto.ClusterSyncStatusResponse;
import io.strato.aiops.adapter.in.web.dto.KubernetesEventSnapshotResponse;
import io.strato.aiops.adapter.in.web.dto.KubernetesNamespaceResponse;
import io.strato.aiops.adapter.in.web.dto.KubernetesNodeResponse;
import io.strato.aiops.adapter.in.web.dto.KubernetesResourceManifestResponse;
import io.strato.aiops.adapter.in.web.dto.KubernetesResourceSnapshotResponse;
import io.strato.aiops.adapter.in.web.dto.RegisterClusterRequest;
import io.strato.aiops.adapter.in.web.dto.StartJobResponse;
import io.strato.aiops.adapter.in.web.dto.UpdateClusterSyncSettingsRequest;
import io.strato.aiops.adapter.in.web.validation.ClusterRegistrationValidator;
import io.strato.aiops.application.port.in.GetClusterRuntimeUseCase;
import io.strato.aiops.application.port.in.GetClusterSnapshotUseCase;
import io.strato.aiops.application.port.in.GetClusterCredentialUseCase;
import io.strato.aiops.application.port.in.GetClusterSyncSettingsUseCase;
import io.strato.aiops.application.port.in.GetClusterUseCase;
import io.strato.aiops.application.port.in.DeleteClusterUseCase;
import io.strato.aiops.application.port.in.RegisterClusterUseCase;
import io.strato.aiops.application.port.in.StartClusterSyncUseCase;
import io.strato.aiops.application.port.in.TestClusterConnectionUseCase;
import io.strato.aiops.application.port.in.UpdateClusterSyncSettingsUseCase;
import io.strato.aiops.domain.cluster.Cluster;
import io.strato.aiops.adapter.in.web.security.ApiAuthorizationInterceptor;
import io.strato.aiops.application.service.IdentityAccessService;
import io.strato.aiops.application.service.ResolvedAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Tag(name = "Clusters", description = "Kubernetes cluster management APIs")
@RestController
@RequestMapping("/api/clusters")
public class ClusterController {

    private final RegisterClusterUseCase registerClusterUseCase;
    private final DeleteClusterUseCase deleteClusterUseCase;
    private final GetClusterUseCase getClusterUseCase;
    private final GetClusterCredentialUseCase getClusterCredentialUseCase;
    private final GetClusterRuntimeUseCase getClusterRuntimeUseCase;
    private final GetClusterSnapshotUseCase getClusterSnapshotUseCase;
    private final GetClusterSyncSettingsUseCase getClusterSyncSettingsUseCase;
    private final TestClusterConnectionUseCase testClusterConnectionUseCase;
    private final UpdateClusterSyncSettingsUseCase updateClusterSyncSettingsUseCase;
    private final StartClusterSyncUseCase startClusterSyncUseCase;
    private final ClusterRegistrationValidator clusterRegistrationValidator;
    private final boolean credentialRevealEnabled;
    private final IdentityAccessService identityAccessService;

    public ClusterController(RegisterClusterUseCase registerClusterUseCase, DeleteClusterUseCase deleteClusterUseCase, GetClusterUseCase getClusterUseCase,
                             GetClusterCredentialUseCase getClusterCredentialUseCase,
                             GetClusterRuntimeUseCase getClusterRuntimeUseCase,
                             GetClusterSnapshotUseCase getClusterSnapshotUseCase,
                             GetClusterSyncSettingsUseCase getClusterSyncSettingsUseCase,
                             TestClusterConnectionUseCase testClusterConnectionUseCase,
                             UpdateClusterSyncSettingsUseCase updateClusterSyncSettingsUseCase,
                             StartClusterSyncUseCase startClusterSyncUseCase,
                             ClusterRegistrationValidator clusterRegistrationValidator,
                             IdentityAccessService identityAccessService,
                             @Value("${aiops.security.credential-reveal-enabled:false}") boolean credentialRevealEnabled) {
        this.registerClusterUseCase = registerClusterUseCase;
        this.deleteClusterUseCase = deleteClusterUseCase;
        this.getClusterUseCase = getClusterUseCase;
        this.getClusterCredentialUseCase = getClusterCredentialUseCase;
        this.getClusterRuntimeUseCase = getClusterRuntimeUseCase;
        this.getClusterSnapshotUseCase = getClusterSnapshotUseCase;
        this.getClusterSyncSettingsUseCase = getClusterSyncSettingsUseCase;
        this.testClusterConnectionUseCase = testClusterConnectionUseCase;
        this.updateClusterSyncSettingsUseCase = updateClusterSyncSettingsUseCase;
        this.startClusterSyncUseCase = startClusterSyncUseCase;
        this.clusterRegistrationValidator = clusterRegistrationValidator;
        this.identityAccessService = identityAccessService;
        this.credentialRevealEnabled = credentialRevealEnabled;
    }

    @Operation(summary = "List Kubernetes clusters")
    @GetMapping
    public List<ClusterResponse> listClusters(@RequestParam(required = false) UUID tenantId,
                                              @RequestParam(required = false) UUID workspaceId,
                                              HttpServletRequest request) {
        ResolvedAccess access = (ResolvedAccess) request.getAttribute(ApiAuthorizationInterceptor.RESOLVED_ACCESS_ATTRIBUTE);
        return getClusterUseCase.listClusters(tenantId, workspaceId).stream()
                .filter(cluster -> isVisible(cluster, access))
                .map(ClusterResponse::from)
                .toList();
    }

    private boolean isVisible(Cluster cluster, ResolvedAccess access) {
        return access == null
                || identityAccessService.hasPlatformScope(access, io.strato.aiops.domain.identity.Capability.CLUSTER_READ)
                || identityAccessService.visibleClusterIds(access).contains(cluster.id());
    }

    @Operation(summary = "Get Kubernetes cluster")
    @GetMapping("/{clusterId}")
    public ClusterResponse getCluster(@PathVariable UUID clusterId) {
        return ClusterResponse.from(getClusterUseCase.getCluster(clusterId));
    }

    @Operation(summary = "Get stored cluster credential")
    @GetMapping("/{clusterId}/credential")
    public ClusterCredentialResponse getClusterCredential(
            @PathVariable UUID clusterId,
            @RequestParam(defaultValue = "false") boolean reveal,
            HttpServletRequest request
    ) {
        if (reveal && !credentialRevealEnabled) {
            throw new CredentialRevealDisabledException();
        }
        return ClusterCredentialResponse.from(getClusterCredentialUseCase.getClusterCredential(
                clusterId,
                reveal,
                actor(request),
                String.valueOf(request.getAttribute(RequestAttributes.REQUEST_ID))
        ));
    }

    @Operation(summary = "Register Kubernetes cluster")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClusterResponse registerCluster(@Valid @RequestBody RegisterClusterRequest request, HttpServletRequest servletRequest) {
        clusterRegistrationValidator.validate(request);
        String actor = actor(servletRequest);
        String requestId = String.valueOf(servletRequest.getAttribute(RequestAttributes.REQUEST_ID));
        Cluster cluster = registerClusterUseCase.registerCluster(request.toCommand(), actor, requestId);
        return ClusterResponse.from(cluster);
    }

    @Operation(summary = "Test Kubernetes cluster connection")
    @PostMapping("/{clusterId}/connection-test")
    public ClusterConnectionTestResponse testClusterConnection(@PathVariable UUID clusterId, HttpServletRequest request) {
        String actor = actor(request);
        String requestId = String.valueOf(request.getAttribute(RequestAttributes.REQUEST_ID));
        return ClusterConnectionTestResponse.from(testClusterConnectionUseCase.testClusterConnection(clusterId, actor, requestId));
    }

    @Operation(summary = "Delete Kubernetes cluster registration")
    @DeleteMapping("/{clusterId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCluster(@PathVariable UUID clusterId, HttpServletRequest request) {
        String actor = actor(request);
        String requestId = String.valueOf(request.getAttribute(RequestAttributes.REQUEST_ID));
        deleteClusterUseCase.deleteCluster(clusterId, actor, requestId);
    }

    @Operation(summary = "List namespaces from Kubernetes API")
    @GetMapping("/{clusterId}/namespaces")
    public List<KubernetesNamespaceResponse> listNamespaces(@PathVariable UUID clusterId) {
        return getClusterRuntimeUseCase.listNamespaces(clusterId).stream()
                .map(KubernetesNamespaceResponse::from)
                .toList();
    }

    @Operation(summary = "List nodes from Kubernetes API")
    @GetMapping("/{clusterId}/nodes")
    public List<KubernetesNodeResponse> listNodes(@PathVariable UUID clusterId) {
        return getClusterRuntimeUseCase.listNodes(clusterId).stream()
                .map(KubernetesNodeResponse::from)
                .toList();
    }

    @Operation(summary = "List latest Kubernetes resource snapshots")
    @GetMapping("/{clusterId}/resources")
    public List<KubernetesResourceSnapshotResponse> listResources(
            @PathVariable UUID clusterId,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String resourceType
    ) {
        return getClusterSnapshotUseCase.listResources(clusterId, namespace, resourceType).stream()
                .map(KubernetesResourceSnapshotResponse::from)
                .toList();
    }

    @Operation(summary = "Page latest Kubernetes resource snapshots with inventory facets")
    @GetMapping("/{clusterId}/resources/page")
    public ClusterResourcePageResponse pageResources(
            @PathVariable UUID clusterId,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String resourceType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        return ClusterResourcePageResponse.from(
                getClusterSnapshotUseCase.pageResources(clusterId, namespace, resourceType, page, size)
        );
    }

    @Operation(summary = "Get live Kubernetes resource manifest")
    @GetMapping("/{clusterId}/resources/{resourceType}/{resourceName}/manifest")
    public KubernetesResourceManifestResponse getResourceManifest(
            @PathVariable UUID clusterId,
            @PathVariable String resourceType,
            @PathVariable String resourceName,
            @RequestParam(required = false) String namespace
    ) {
        return KubernetesResourceManifestResponse.from(getClusterRuntimeUseCase.getResourceManifest(
                clusterId,
                namespace,
                resourceType,
                resourceName
        ));
    }

    @Operation(summary = "List latest Kubernetes event snapshots")
    @GetMapping("/{clusterId}/events")
    public List<KubernetesEventSnapshotResponse> listEvents(
            @PathVariable UUID clusterId,
            @RequestParam(required = false) String namespace
    ) {
        return getClusterSnapshotUseCase.listEvents(clusterId, namespace).stream()
                .map(KubernetesEventSnapshotResponse::from)
                .toList();
    }

    @Operation(summary = "Get latest cluster synchronization status")
    @GetMapping("/{clusterId}/sync-status")
    public ClusterSyncStatusResponse getLatestSyncStatus(@PathVariable UUID clusterId) {
        return getClusterSnapshotUseCase.getLatestSyncStatus(clusterId)
                .map(ClusterSyncStatusResponse::from)
                .orElseThrow(() -> new NoSuchElementException("Sync status not found: " + clusterId));
    }

    @Operation(summary = "Update cluster synchronization settings")
    @PutMapping("/{clusterId}/sync-settings")
    public ClusterSyncSettingsResponse updateSyncSettings(
            @PathVariable UUID clusterId,
            @Valid @RequestBody UpdateClusterSyncSettingsRequest request,
            HttpServletRequest servletRequest
    ) {
        String actor = actor(servletRequest);
        String requestId = String.valueOf(servletRequest.getAttribute(RequestAttributes.REQUEST_ID));
        return ClusterSyncSettingsResponse.from(updateClusterSyncSettingsUseCase.updateClusterSyncSettings(
                clusterId,
                request.toCommand(),
                actor,
                requestId
        ));
    }

    @Operation(summary = "Get cluster synchronization settings")
    @GetMapping("/{clusterId}/sync-settings")
    public ClusterSyncSettingsResponse getSyncSettings(@PathVariable UUID clusterId) {
        return ClusterSyncSettingsResponse.from(getClusterSyncSettingsUseCase.getClusterSyncSettings(clusterId));
    }

    @Operation(summary = "Start cluster synchronization")
    @PostMapping("/{clusterId}/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StartJobResponse syncCluster(@PathVariable UUID clusterId, HttpServletRequest request) {
        String actor = actor(request);
        String requestId = String.valueOf(request.getAttribute(RequestAttributes.REQUEST_ID));
        return new StartJobResponse(startClusterSyncUseCase.startClusterSync(clusterId, actor, requestId));
    }

    private String actor(HttpServletRequest request) {
        return request.getUserPrincipal() == null ? "anonymous" : request.getUserPrincipal().getName();
    }
}
