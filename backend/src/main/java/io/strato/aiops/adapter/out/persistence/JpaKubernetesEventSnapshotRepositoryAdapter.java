package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.KubernetesEventSnapshotRepositoryPort;
import io.strato.aiops.domain.sync.KubernetesEventSnapshot;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class JpaKubernetesEventSnapshotRepositoryAdapter implements KubernetesEventSnapshotRepositoryPort {

    private final KubernetesEventSnapshotJpaRepository repository;

    public JpaKubernetesEventSnapshotRepositoryAdapter(KubernetesEventSnapshotJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<KubernetesEventSnapshot> saveAll(List<KubernetesEventSnapshot> snapshots) {
        return repository.saveAll(snapshots.stream()
                        .map(KubernetesEventSnapshotEntity::fromDomain)
                        .toList())
                .stream()
                .map(KubernetesEventSnapshotEntity::toDomain)
                .toList();
    }

    @Override
    public long countBySyncJobId(UUID syncJobId) {
        return repository.countBySyncJobId(syncJobId);
    }

    @Override
    public List<KubernetesEventSnapshot> findLatest(UUID clusterId, String namespace, int limit) {
        return repository.findLatest(clusterId, namespace, PageRequest.of(0, limit)).stream()
                .map(KubernetesEventSnapshotEntity::toDomain)
                .toList();
    }

    @Override
    public List<KubernetesEventSnapshot> findBySyncJobId(UUID syncJobId, String namespace, int limit) {
        return repository.findBySyncJobId(syncJobId, namespace, PageRequest.of(0, limit)).stream()
                .map(KubernetesEventSnapshotEntity::toDomain)
                .toList();
    }
}
