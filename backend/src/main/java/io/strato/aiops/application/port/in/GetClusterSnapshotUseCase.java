package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.sync.KubernetesEventSnapshot;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetClusterSnapshotUseCase {

    List<KubernetesResourceSnapshot> listResources(UUID clusterId, String namespace, String resourceType);

    ClusterResourcePageResult pageResources(UUID clusterId, String namespace, String resourceType, int page, int size);

    List<KubernetesEventSnapshot> listEvents(UUID clusterId, String namespace);

    Optional<ClusterSyncStatusResult> getLatestSyncStatus(UUID clusterId);
}
