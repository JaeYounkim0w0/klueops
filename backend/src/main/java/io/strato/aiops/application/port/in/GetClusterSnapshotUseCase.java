package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.sync.KubernetesEventSnapshot;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetClusterSnapshotUseCase {

    /** GetClusterSnapshotUseCase의 listResources 처리 결과를 조회해 반환한다. */
    List<KubernetesResourceSnapshot> listResources(UUID clusterId, String namespace, String resourceType);

    /** GetClusterSnapshotUseCase의 pageResources 처리 계약을 정의한다. */
    ClusterResourcePageResult pageResources(UUID clusterId, String namespace, String resourceType, int page, int size);

    /** GetClusterSnapshotUseCase의 listEvents 처리 결과를 조회해 반환한다. */
    List<KubernetesEventSnapshot> listEvents(UUID clusterId, String namespace);

    /** GetClusterSnapshotUseCase의 getLatestSyncStatus 처리 결과를 조회해 반환한다. */
    Optional<ClusterSyncStatusResult> getLatestSyncStatus(UUID clusterId);
}
