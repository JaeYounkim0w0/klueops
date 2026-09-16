package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;

import java.util.List;
import java.util.UUID;

public interface KubernetesResourceSnapshotRepositoryPort {

    record ResourcePage(List<KubernetesResourceSnapshot> items, long totalElements, int totalPages) {
    }

    record Facet(String value, long count) {
    }

    /** KubernetesResourceSnapshotRepositoryPort의 saveAll 처리에 필요한 데이터를 생성하거나 저장한다. */
    List<KubernetesResourceSnapshot> saveAll(List<KubernetesResourceSnapshot> snapshots);

    /** KubernetesResourceSnapshotRepositoryPort의 countBySyncJobId 처리 계약을 정의한다. */
    long countBySyncJobId(UUID syncJobId);

    /** KubernetesResourceSnapshotRepositoryPort의 findLatest 처리 결과를 조회해 반환한다. */
    List<KubernetesResourceSnapshot> findLatest(UUID clusterId, String namespace, String resourceType, int limit);

    /** KubernetesResourceSnapshotRepositoryPort의 findBySyncJobId 처리 결과를 조회해 반환한다. */
    List<KubernetesResourceSnapshot> findBySyncJobId(UUID syncJobId, String namespace, String resourceType, int limit);

    /** KubernetesResourceSnapshotRepositoryPort의 findPageBySyncJobId 처리 결과를 조회해 반환한다. */
    ResourcePage findPageBySyncJobId(UUID syncJobId, String namespace, String resourceType, int page, int size);

    /** KubernetesResourceSnapshotRepositoryPort의 countNamespacesBySyncJobId 처리 계약을 정의한다. */
    List<Facet> countNamespacesBySyncJobId(UUID syncJobId);

    /** KubernetesResourceSnapshotRepositoryPort의 countResourceTypesBySyncJobId 처리 계약을 정의한다. */
    List<Facet> countResourceTypesBySyncJobId(UUID syncJobId, String namespace);

    /** KubernetesResourceSnapshotRepositoryPort의 countProblemsBySyncJobId 처리 계약을 정의한다. */
    long countProblemsBySyncJobId(UUID syncJobId, String namespace, String resourceType);
}
