package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.cluster.Cluster;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClusterRepositoryPort {

    Cluster save(Cluster cluster);

    Optional<Cluster> findById(UUID clusterId);

    default void lockById(UUID clusterId) {
        findById(clusterId).orElseThrow();
    }

    List<Cluster> findAll();

    default List<Cluster> findAll(UUID tenantId, UUID workspaceId) {
        return findAll().stream()
                .filter(cluster -> tenantId == null || tenantId.equals(cluster.tenantId()))
                .filter(cluster -> workspaceId == null || workspaceId.equals(cluster.workspaceId()))
                .toList();
    }
}
