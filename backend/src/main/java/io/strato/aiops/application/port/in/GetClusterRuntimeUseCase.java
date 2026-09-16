package io.strato.aiops.application.port.in;

import java.util.List;
import java.util.UUID;

public interface GetClusterRuntimeUseCase {

    /** GetClusterRuntimeUseCase의 listNamespaces 처리 결과를 조회해 반환한다. */
    List<KubernetesNamespaceSummary> listNamespaces(UUID clusterId);

    /** GetClusterRuntimeUseCase의 listNodes 처리 결과를 조회해 반환한다. */
    List<KubernetesNodeSummary> listNodes(UUID clusterId);

    /** GetClusterRuntimeUseCase의 getResourceManifest 처리 결과를 조회해 반환한다. */
    KubernetesResourceManifestResult getResourceManifest(UUID clusterId, String namespace, String resourceType, String resourceName);
}
