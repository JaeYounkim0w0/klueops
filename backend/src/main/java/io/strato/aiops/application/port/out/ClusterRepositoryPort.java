package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.cluster.Cluster;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClusterRepositoryPort {

    /** ClusterRepositoryPort의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    Cluster save(Cluster cluster);

    /** ClusterRepositoryPort의 findById 처리 결과를 조회해 반환한다. */
    Optional<Cluster> findById(UUID clusterId);

    /** ClusterRepositoryPort의 lockById 처리에 필요한 업무 로직을 수행한다. */
    default void lockById(UUID clusterId) {
        findById(clusterId).orElseThrow();
    }

    /** ClusterRepositoryPort의 findAll 처리 결과를 조회해 반환한다. */
    List<Cluster> findAll();

    /** ClusterRepositoryPort의 findAll 처리 결과를 조회해 반환한다. */
    default List<Cluster> findAll(UUID tenantId, UUID workspaceId) {
        return findAll().stream()
                .filter(cluster -> tenantId == null || tenantId.equals(cluster.tenantId()))
                .filter(cluster -> workspaceId == null || workspaceId.equals(cluster.workspaceId()))
                .toList();
    }
}
