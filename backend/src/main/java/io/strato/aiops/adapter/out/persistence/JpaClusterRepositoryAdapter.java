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

    /** JpaClusterRepositoryAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public JpaClusterRepositoryAdapter(ClusterJpaRepository clusterJpaRepository) {
        this.clusterJpaRepository = clusterJpaRepository;
    }

    /** JpaClusterRepositoryAdapter의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public Cluster save(Cluster cluster) {
        return clusterJpaRepository.save(ClusterEntity.fromDomain(cluster)).toDomain();
    }

    /** JpaClusterRepositoryAdapter의 findById 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<Cluster> findById(UUID clusterId) {
        return clusterJpaRepository.findById(clusterId).map(ClusterEntity::toDomain);
    }

    /** JpaClusterRepositoryAdapter의 lockById 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public void lockById(UUID clusterId) {
        clusterJpaRepository.findByIdForUpdate(clusterId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Cluster not found: " + clusterId));
    }

    /** JpaClusterRepositoryAdapter의 findAll 처리 결과를 조회해 반환한다. */
    @Override
    public List<Cluster> findAll() {
        return clusterJpaRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(ClusterEntity::toDomain)
                .toList();
    }

    /** JpaClusterRepositoryAdapter의 findAll 처리 결과를 조회해 반환한다. */
    @Override
    public List<Cluster> findAll(UUID tenantId, UUID workspaceId) {
        if (tenantId == null) return findAll();
        List<ClusterEntity> entities = workspaceId == null
                ? clusterJpaRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)
                : clusterJpaRepository.findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(tenantId, workspaceId);
        return entities.stream().map(ClusterEntity::toDomain).toList();
    }
}
