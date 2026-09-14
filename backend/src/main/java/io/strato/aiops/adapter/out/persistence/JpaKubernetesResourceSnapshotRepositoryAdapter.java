package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.KubernetesResourceSnapshotRepositoryPort;
import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class JpaKubernetesResourceSnapshotRepositoryAdapter implements KubernetesResourceSnapshotRepositoryPort {

    private final KubernetesResourceSnapshotJpaRepository repository;

    public JpaKubernetesResourceSnapshotRepositoryAdapter(KubernetesResourceSnapshotJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<KubernetesResourceSnapshot> saveAll(List<KubernetesResourceSnapshot> snapshots) {
        return repository.saveAll(snapshots.stream()
                        .map(KubernetesResourceSnapshotEntity::fromDomain)
                        .toList())
                .stream()
                .map(KubernetesResourceSnapshotEntity::toDomain)
                .toList();
    }

    @Override
    public long countBySyncJobId(UUID syncJobId) {
        return repository.countBySyncJobId(syncJobId);
    }

    @Override
    public List<KubernetesResourceSnapshot> findLatest(UUID clusterId, String namespace, String resourceType, int limit) {
        return latestPage(clusterId, namespace, resourceType, PageRequest.of(0, limit)).stream()
                .map(KubernetesResourceSnapshotEntity::toDomain)
                .toList();
    }

    @Override
    public List<KubernetesResourceSnapshot> findBySyncJobId(UUID syncJobId, String namespace, String resourceType, int limit) {
        return syncPage(syncJobId, namespace, resourceType, PageRequest.of(0, limit)).stream()
                .map(KubernetesResourceSnapshotEntity::toDomain)
                .toList();
    }

    @Override
    public ResourcePage findPageBySyncJobId(UUID syncJobId, String namespace, String resourceType, int page, int size) {
        var result = syncPage(syncJobId, namespace, resourceType, PageRequest.of(page, size));
        return new ResourcePage(
                result.getContent().stream().map(KubernetesResourceSnapshotEntity::toDomain).toList(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Override
    public List<Facet> countNamespacesBySyncJobId(UUID syncJobId) {
        return facets(repository.countNamespacesBySyncJobId(syncJobId));
    }

    @Override
    public List<Facet> countResourceTypesBySyncJobId(UUID syncJobId, String namespace) {
        return facets(namespace == null
                ? repository.countResourceTypesBySyncJobId(syncJobId)
                : repository.countResourceTypesBySyncJobIdAndNamespace(syncJobId, namespace));
    }

    @Override
    public long countProblemsBySyncJobId(UUID syncJobId, String namespace, String resourceType) {
        if (namespace == null && resourceType == null) return repository.countProblemsBySyncJobId(syncJobId);
        if (resourceType == null) return repository.countProblemsBySyncJobIdAndNamespace(syncJobId, namespace);
        if (namespace == null) return repository.countProblemsBySyncJobIdAndResourceType(syncJobId, resourceType);
        return repository.countProblemsBySyncJobIdAndNamespaceAndResourceType(syncJobId, namespace, resourceType);
    }

    private Page<KubernetesResourceSnapshotEntity> latestPage(UUID clusterId, String namespace, String resourceType,
                                                               PageRequest page) {
        if (namespace == null && resourceType == null) return repository.findByClusterIdOrderByCollectedAtDesc(clusterId, page);
        if (resourceType == null) return repository.findByClusterIdAndNamespaceOrderByCollectedAtDesc(clusterId, namespace, page);
        if (namespace == null) return repository.findByClusterIdAndResourceTypeOrderByCollectedAtDesc(clusterId, resourceType, page);
        return repository.findByClusterIdAndNamespaceAndResourceTypeOrderByCollectedAtDesc(clusterId, namespace, resourceType, page);
    }

    private Page<KubernetesResourceSnapshotEntity> syncPage(UUID syncJobId, String namespace, String resourceType,
                                                             PageRequest page) {
        if (namespace == null && resourceType == null) {
            return repository.findBySyncJobIdOrderByResourceTypeAscResourceNameAsc(syncJobId, page);
        }
        if (resourceType == null) {
            return repository.findBySyncJobIdAndNamespaceOrderByResourceTypeAscResourceNameAsc(syncJobId, namespace, page);
        }
        if (namespace == null) {
            return repository.findBySyncJobIdAndResourceTypeOrderByResourceTypeAscResourceNameAsc(syncJobId, resourceType, page);
        }
        return repository.findBySyncJobIdAndNamespaceAndResourceTypeOrderByResourceTypeAscResourceNameAsc(
                syncJobId, namespace, resourceType, page);
    }

    private List<Facet> facets(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new Facet(String.valueOf(row[0]), ((Number) row[1]).longValue()))
                .toList();
    }
}
