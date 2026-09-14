package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

interface KubernetesEventSnapshotJpaRepository extends JpaRepository<KubernetesEventSnapshotEntity, UUID> {

    long countBySyncJobId(UUID syncJobId);

    @Query("""
            select e from KubernetesEventSnapshotEntity e
            where e.clusterId = :clusterId
              and (:namespace is null or e.namespace = :namespace)
            order by e.collectedAt desc
            """)
    List<KubernetesEventSnapshotEntity> findLatest(UUID clusterId, String namespace, Pageable pageable);

    @Query("""
            select e from KubernetesEventSnapshotEntity e
            where e.syncJobId = :syncJobId
              and (:namespace is null or e.namespace = :namespace)
            order by e.eventTime desc, e.collectedAt desc
            """)
    List<KubernetesEventSnapshotEntity> findBySyncJobId(UUID syncJobId, String namespace, Pageable pageable);
}
