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

    /** JpaKubernetesEventSnapshotRepositoryAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public JpaKubernetesEventSnapshotRepositoryAdapter(KubernetesEventSnapshotJpaRepository repository) {
        this.repository = repository;
    }

    /** JpaKubernetesEventSnapshotRepositoryAdapter의 saveAll 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public List<KubernetesEventSnapshot> saveAll(List<KubernetesEventSnapshot> snapshots) {
        return repository.saveAll(snapshots.stream()
                        .map(KubernetesEventSnapshotEntity::fromDomain)
                        .toList())
                .stream()
                .map(KubernetesEventSnapshotEntity::toDomain)
                .toList();
    }

    /** JpaKubernetesEventSnapshotRepositoryAdapter의 countBySyncJobId 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public long countBySyncJobId(UUID syncJobId) {
        return repository.countBySyncJobId(syncJobId);
    }

    /** JpaKubernetesEventSnapshotRepositoryAdapter의 findLatest 처리 결과를 조회해 반환한다. */
    @Override
    public List<KubernetesEventSnapshot> findLatest(UUID clusterId, String namespace, int limit) {
        return repository.findLatest(clusterId, namespace, PageRequest.of(0, limit)).stream()
                .map(KubernetesEventSnapshotEntity::toDomain)
                .toList();
    }

    /** JpaKubernetesEventSnapshotRepositoryAdapter의 findBySyncJobId 처리 결과를 조회해 반환한다. */
    @Override
    public List<KubernetesEventSnapshot> findBySyncJobId(UUID syncJobId, String namespace, int limit) {
        return repository.findBySyncJobId(syncJobId, namespace, PageRequest.of(0, limit)).stream()
                .map(KubernetesEventSnapshotEntity::toDomain)
                .toList();
    }
}
