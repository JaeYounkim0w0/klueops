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

    /** JpaKubernetesResourceSnapshotRepositoryAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public JpaKubernetesResourceSnapshotRepositoryAdapter(KubernetesResourceSnapshotJpaRepository repository) {
        this.repository = repository;
    }

    /** JpaKubernetesResourceSnapshotRepositoryAdapter의 saveAll 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public List<KubernetesResourceSnapshot> saveAll(List<KubernetesResourceSnapshot> snapshots) {
        return repository.saveAll(snapshots.stream()
                        .map(KubernetesResourceSnapshotEntity::fromDomain)
                        .toList())
                .stream()
                .map(KubernetesResourceSnapshotEntity::toDomain)
                .toList();
    }

    /** JpaKubernetesResourceSnapshotRepositoryAdapter의 countBySyncJobId 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public long countBySyncJobId(UUID syncJobId) {
        return repository.countBySyncJobId(syncJobId);
    }

    /** JpaKubernetesResourceSnapshotRepositoryAdapter의 findLatest 처리 결과를 조회해 반환한다. */
    @Override
    public List<KubernetesResourceSnapshot> findLatest(UUID clusterId, String namespace, String resourceType, int limit) {
        return latestPage(clusterId, namespace, resourceType, PageRequest.of(0, limit)).stream()
                .map(KubernetesResourceSnapshotEntity::toDomain)
                .toList();
    }

    /** JpaKubernetesResourceSnapshotRepositoryAdapter의 findBySyncJobId 처리 결과를 조회해 반환한다. */
    @Override
    public List<KubernetesResourceSnapshot> findBySyncJobId(UUID syncJobId, String namespace, String resourceType, int limit) {
        return syncPage(syncJobId, namespace, resourceType, PageRequest.of(0, limit)).stream()
                .map(KubernetesResourceSnapshotEntity::toDomain)
                .toList();
    }

    /** JpaKubernetesResourceSnapshotRepositoryAdapter의 findPageBySyncJobId 처리 결과를 조회해 반환한다. */
    @Override
    public ResourcePage findPageBySyncJobId(UUID syncJobId, String namespace, String resourceType, int page, int size) {
        var result = syncPage(syncJobId, namespace, resourceType, PageRequest.of(page, size));
        return new ResourcePage(
                result.getContent().stream().map(KubernetesResourceSnapshotEntity::toDomain).toList(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    /** JpaKubernetesResourceSnapshotRepositoryAdapter의 countNamespacesBySyncJobId 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public List<Facet> countNamespacesBySyncJobId(UUID syncJobId) {
        return facets(repository.countNamespacesBySyncJobId(syncJobId));
    }

    /** JpaKubernetesResourceSnapshotRepositoryAdapter의 countResourceTypesBySyncJobId 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public List<Facet> countResourceTypesBySyncJobId(UUID syncJobId, String namespace) {
        return facets(namespace == null
                ? repository.countResourceTypesBySyncJobId(syncJobId)
                : repository.countResourceTypesBySyncJobIdAndNamespace(syncJobId, namespace));
    }

    /** JpaKubernetesResourceSnapshotRepositoryAdapter의 countProblemsBySyncJobId 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public long countProblemsBySyncJobId(UUID syncJobId, String namespace, String resourceType) {
        if (namespace == null && resourceType == null) return repository.countProblemsBySyncJobId(syncJobId);
        if (resourceType == null) return repository.countProblemsBySyncJobIdAndNamespace(syncJobId, namespace);
        if (namespace == null) return repository.countProblemsBySyncJobIdAndResourceType(syncJobId, resourceType);
        return repository.countProblemsBySyncJobIdAndNamespaceAndResourceType(syncJobId, namespace, resourceType);
    }

    /** JpaKubernetesResourceSnapshotRepositoryAdapter의 latestPage 처리에 필요한 업무 로직을 수행한다. */
    private Page<KubernetesResourceSnapshotEntity> latestPage(UUID clusterId, String namespace, String resourceType,
                                                               PageRequest page) {
        if (namespace == null && resourceType == null) return repository.findByClusterIdOrderByCollectedAtDesc(clusterId, page);
        if (resourceType == null) return repository.findByClusterIdAndNamespaceOrderByCollectedAtDesc(clusterId, namespace, page);
        if (namespace == null) return repository.findByClusterIdAndResourceTypeOrderByCollectedAtDesc(clusterId, resourceType, page);
        return repository.findByClusterIdAndNamespaceAndResourceTypeOrderByCollectedAtDesc(clusterId, namespace, resourceType, page);
    }

    /** JpaKubernetesResourceSnapshotRepositoryAdapter의 syncPage 처리의 핵심 작업 흐름을 실행한다. */
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

    /** JpaKubernetesResourceSnapshotRepositoryAdapter의 facets 처리에 필요한 업무 로직을 수행한다. */
    private List<Facet> facets(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new Facet(String.valueOf(row[0]), ((Number) row[1]).longValue()))
                .toList();
    }
}
