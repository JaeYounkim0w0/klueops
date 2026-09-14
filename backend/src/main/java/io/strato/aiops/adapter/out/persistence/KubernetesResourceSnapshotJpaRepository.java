package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

interface KubernetesResourceSnapshotJpaRepository extends JpaRepository<KubernetesResourceSnapshotEntity, UUID> {

    long countBySyncJobId(UUID syncJobId);

    Page<KubernetesResourceSnapshotEntity> findByClusterIdOrderByCollectedAtDesc(UUID clusterId, Pageable pageable);

    Page<KubernetesResourceSnapshotEntity> findByClusterIdAndNamespaceOrderByCollectedAtDesc(
            UUID clusterId, String namespace, Pageable pageable);

    Page<KubernetesResourceSnapshotEntity> findByClusterIdAndResourceTypeOrderByCollectedAtDesc(
            UUID clusterId, String resourceType, Pageable pageable);

    Page<KubernetesResourceSnapshotEntity> findByClusterIdAndNamespaceAndResourceTypeOrderByCollectedAtDesc(
            UUID clusterId, String namespace, String resourceType, Pageable pageable);

    Page<KubernetesResourceSnapshotEntity> findBySyncJobIdOrderByResourceTypeAscResourceNameAsc(
            UUID syncJobId, Pageable pageable);

    Page<KubernetesResourceSnapshotEntity> findBySyncJobIdAndNamespaceOrderByResourceTypeAscResourceNameAsc(
            UUID syncJobId, String namespace, Pageable pageable);

    Page<KubernetesResourceSnapshotEntity> findBySyncJobIdAndResourceTypeOrderByResourceTypeAscResourceNameAsc(
            UUID syncJobId, String resourceType, Pageable pageable);

    Page<KubernetesResourceSnapshotEntity> findBySyncJobIdAndNamespaceAndResourceTypeOrderByResourceTypeAscResourceNameAsc(
            UUID syncJobId, String namespace, String resourceType, Pageable pageable);

    @Query("""
            select r.namespace, count(r) from KubernetesResourceSnapshotEntity r
            where r.syncJobId = :syncJobId and r.namespace is not null and r.namespace <> ''
            group by r.namespace order by r.namespace asc
            """)
    List<Object[]> countNamespacesBySyncJobId(UUID syncJobId);

    @Query("""
            select r.resourceType, count(r) from KubernetesResourceSnapshotEntity r
            where r.syncJobId = :syncJobId
            group by r.resourceType order by r.resourceType asc
            """)
    List<Object[]> countResourceTypesBySyncJobId(UUID syncJobId);

    @Query("""
            select r.resourceType, count(r) from KubernetesResourceSnapshotEntity r
            where r.syncJobId = :syncJobId and r.namespace = :namespace
            group by r.resourceType order by r.resourceType asc
            """)
    List<Object[]> countResourceTypesBySyncJobIdAndNamespace(UUID syncJobId, String namespace);

    @Query("""
            select count(r) from KubernetesResourceSnapshotEntity r
            where r.syncJobId = :syncJobId
              and (
                lower(coalesce(r.status, '')) like '%pending%'
                or lower(coalesce(r.status, '')) like '%failed%'
                or lower(coalesce(r.status, '')) like '%error%'
                or lower(coalesce(r.status, '')) like '%crash%'
                or lower(coalesce(r.status, '')) like '%0/%'
              )
            """)
    long countProblemsBySyncJobId(UUID syncJobId);

    @Query("""
            select count(r) from KubernetesResourceSnapshotEntity r
            where r.syncJobId = :syncJobId and r.namespace = :namespace
              and (
                lower(coalesce(r.status, '')) like '%pending%'
                or lower(coalesce(r.status, '')) like '%failed%'
                or lower(coalesce(r.status, '')) like '%error%'
                or lower(coalesce(r.status, '')) like '%crash%'
                or lower(coalesce(r.status, '')) like '%0/%'
              )
            """)
    long countProblemsBySyncJobIdAndNamespace(UUID syncJobId, String namespace);

    @Query("""
            select count(r) from KubernetesResourceSnapshotEntity r
            where r.syncJobId = :syncJobId and r.resourceType = :resourceType
              and (
                lower(coalesce(r.status, '')) like '%pending%'
                or lower(coalesce(r.status, '')) like '%failed%'
                or lower(coalesce(r.status, '')) like '%error%'
                or lower(coalesce(r.status, '')) like '%crash%'
                or lower(coalesce(r.status, '')) like '%0/%'
              )
            """)
    long countProblemsBySyncJobIdAndResourceType(UUID syncJobId, String resourceType);

    @Query("""
            select count(r) from KubernetesResourceSnapshotEntity r
            where r.syncJobId = :syncJobId and r.namespace = :namespace and r.resourceType = :resourceType
              and (
                lower(coalesce(r.status, '')) like '%pending%'
                or lower(coalesce(r.status, '')) like '%failed%'
                or lower(coalesce(r.status, '')) like '%error%'
                or lower(coalesce(r.status, '')) like '%crash%'
                or lower(coalesce(r.status, '')) like '%0/%'
              )
            """)
    long countProblemsBySyncJobIdAndNamespaceAndResourceType(UUID syncJobId, String namespace, String resourceType);
}
