package io.strato.aiops.application.port.in;

import java.util.List;
import java.util.UUID;

public interface GetClusterRuntimeUseCase {

    List<KubernetesNamespaceSummary> listNamespaces(UUID clusterId);

    List<KubernetesNodeSummary> listNodes(UUID clusterId);

    KubernetesResourceManifestResult getResourceManifest(UUID clusterId, String namespace, String resourceType, String resourceName);
}
