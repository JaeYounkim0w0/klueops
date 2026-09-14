package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.sync.KubernetesResourceSnapshot;

import java.util.List;
import java.util.UUID;

public interface KubernetesResourceSnapshotRepositoryPort {

    record ResourcePage(List<KubernetesResourceSnapshot> items, long totalElements, int totalPages) {
    }

    record Facet(String value, long count) {
    }

    List<KubernetesResourceSnapshot> saveAll(List<KubernetesResourceSnapshot> snapshots);

    long countBySyncJobId(UUID syncJobId);

    List<KubernetesResourceSnapshot> findLatest(UUID clusterId, String namespace, String resourceType, int limit);

    List<KubernetesResourceSnapshot> findBySyncJobId(UUID syncJobId, String namespace, String resourceType, int limit);

    ResourcePage findPageBySyncJobId(UUID syncJobId, String namespace, String resourceType, int page, int size);

    List<Facet> countNamespacesBySyncJobId(UUID syncJobId);

    List<Facet> countResourceTypesBySyncJobId(UUID syncJobId, String namespace);

    long countProblemsBySyncJobId(UUID syncJobId, String namespace, String resourceType);
}
