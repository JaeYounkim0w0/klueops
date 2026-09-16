package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.sync.KubernetesEventSnapshot;

import java.util.List;
import java.util.UUID;

public interface KubernetesEventSnapshotRepositoryPort {

    /** KubernetesEventSnapshotRepositoryPort의 saveAll 처리에 필요한 데이터를 생성하거나 저장한다. */
    List<KubernetesEventSnapshot> saveAll(List<KubernetesEventSnapshot> snapshots);

    /** KubernetesEventSnapshotRepositoryPort의 countBySyncJobId 처리 계약을 정의한다. */
    long countBySyncJobId(UUID syncJobId);

    /** KubernetesEventSnapshotRepositoryPort의 findLatest 처리 결과를 조회해 반환한다. */
    List<KubernetesEventSnapshot> findLatest(UUID clusterId, String namespace, int limit);

    /** KubernetesEventSnapshotRepositoryPort의 findBySyncJobId 처리 결과를 조회해 반환한다. */
    List<KubernetesEventSnapshot> findBySyncJobId(UUID syncJobId, String namespace, int limit);
}
