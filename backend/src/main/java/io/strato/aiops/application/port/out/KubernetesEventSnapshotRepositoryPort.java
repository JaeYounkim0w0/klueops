package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.sync.KubernetesEventSnapshot;

import java.util.List;
import java.util.UUID;

public interface KubernetesEventSnapshotRepositoryPort {

    List<KubernetesEventSnapshot> saveAll(List<KubernetesEventSnapshot> snapshots);

    long countBySyncJobId(UUID syncJobId);

    List<KubernetesEventSnapshot> findLatest(UUID clusterId, String namespace, int limit);

    List<KubernetesEventSnapshot> findBySyncJobId(UUID syncJobId, String namespace, int limit);
}
