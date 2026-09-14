package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.domain.cluster.Cluster;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaClusterRepositoryAdapter implements ClusterRepositoryPort {

    private final ClusterJpaRepository clusterJpaRepository;

    public JpaClusterRepositoryAdapter(ClusterJpaRepository clusterJpaRepository) {
        this.clusterJpaRepository = clusterJpaRepository;
    }

    @Override
    public Cluster save(Cluster cluster) {
        return clusterJpaRepository.save(ClusterEntity.fromDomain(cluster)).toDomain();
    }

    @Override
    public Optional<Cluster> findById(UUID clusterId) {
        return clusterJpaRepository.findById(clusterId).map(ClusterEntity::toDomain);
    }

    @Override
    public void lockById(UUID clusterId) {
        clusterJpaRepository.findByIdForUpdate(clusterId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Cluster not found: " + clusterId));
    }

    @Override
    public List<Cluster> findAll() {
        return clusterJpaRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(ClusterEntity::toDomain)
                .toList();
    }

    @Override
    public List<Cluster> findAll(UUID tenantId, UUID workspaceId) {
        if (tenantId == null) return findAll();
        List<ClusterEntity> entities = workspaceId == null
                ? clusterJpaRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                : clusterJpaRepository.findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(tenantId, workspaceId);
        return entities.stream().map(ClusterEntity::toDomain).toList();
    }
}
