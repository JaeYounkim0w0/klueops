package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

interface KubernetesEventSnapshotJpaRepository extends JpaRepository<KubernetesEventSnapshotEntity, UUID> {

    /** KubernetesEventSnapshotJpaRepository의 countBySyncJobId 처리 계약을 정의한다. */
    long countBySyncJobId(UUID syncJobId);

    /** KubernetesEventSnapshotJpaRepository의 findLatest 처리 결과를 조회해 반환한다. */
    @Query("""
            select e from KubernetesEventSnapshotEntity e
            where e.clusterId = :clusterId
              and (:namespace is null or e.namespace = :namespace)
            order by e.collectedAt desc
            """)
    List<KubernetesEventSnapshotEntity> findLatest(UUID clusterId, String namespace, Pageable pageable);

    /** KubernetesEventSnapshotJpaRepository의 findBySyncJobId 처리 결과를 조회해 반환한다. */
    @Query("""
            select e from KubernetesEventSnapshotEntity e
            where e.syncJobId = :syncJobId
              and (:namespace is null or e.namespace = :namespace)
            order by e.eventTime desc, e.collectedAt desc
            """)
    List<KubernetesEventSnapshotEntity> findBySyncJobId(UUID syncJobId, String namespace, Pageable pageable);
}
