package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

interface KubernetesResourceSnapshotJpaRepository extends JpaRepository<KubernetesResourceSnapshotEntity, UUID> {

    /** KubernetesResourceSnapshotJpaRepository의 countBySyncJobId 처리 계약을 정의한다. */
    long countBySyncJobId(UUID syncJobId);

    /** KubernetesResourceSnapshotJpaRepository의 findByClusterIdOrderByCollectedAtDesc 처리 결과를 조회해 반환한다. */
    Page<KubernetesResourceSnapshotEntity> findByClusterIdOrderByCollectedAtDesc(UUID clusterId, Pageable pageable);

    /** KubernetesResourceSnapshotJpaRepository의 findByClusterIdAndNamespaceOrderByCollectedAtDesc 처리 결과를 조회해 반환한다. */
    Page<KubernetesResourceSnapshotEntity> findByClusterIdAndNamespaceOrderByCollectedAtDesc(
            UUID clusterId, String namespace, Pageable pageable);

    /** KubernetesResourceSnapshotJpaRepository의 findByClusterIdAndResourceTypeOrderByCollectedAtDesc 처리 결과를 조회해 반환한다. */
    Page<KubernetesResourceSnapshotEntity> findByClusterIdAndResourceTypeOrderByCollectedAtDesc(
            UUID clusterId, String resourceType, Pageable pageable);

    /** KubernetesResourceSnapshotJpaRepository의 findByClusterIdAndNamespaceAndResourceTypeOrderByCollectedAtDesc 처리 결과를 조회해 반환한다. */
    Page<KubernetesResourceSnapshotEntity> findByClusterIdAndNamespaceAndResourceTypeOrderByCollectedAtDesc(
            UUID clusterId, String namespace, String resourceType, Pageable pageable);

    /** KubernetesResourceSnapshotJpaRepository의 findBySyncJobIdOrderByResourceTypeAscResourceNameAsc 처리 결과를 조회해 반환한다. */
    Page<KubernetesResourceSnapshotEntity> findBySyncJobIdOrderByResourceTypeAscResourceNameAsc(
            UUID syncJobId, Pageable pageable);

    /** KubernetesResourceSnapshotJpaRepository의 findBySyncJobIdAndNamespaceOrderByResourceTypeAscResourceNameAsc 처리 결과를 조회해 반환한다. */
    Page<KubernetesResourceSnapshotEntity> findBySyncJobIdAndNamespaceOrderByResourceTypeAscResourceNameAsc(
            UUID syncJobId, String namespace, Pageable pageable);

    /** KubernetesResourceSnapshotJpaRepository의 findBySyncJobIdAndResourceTypeOrderByResourceTypeAscResourceNameAsc 처리 결과를 조회해 반환한다. */
    Page<KubernetesResourceSnapshotEntity> findBySyncJobIdAndResourceTypeOrderByResourceTypeAscResourceNameAsc(
            UUID syncJobId, String resourceType, Pageable pageable);

    /** KubernetesResourceSnapshotJpaRepository의 findBySyncJobIdAndNamespaceAndResourceTypeOrderByResourceTypeAscResourceNameAsc 처리 결과를 조회해 반환한다. */
    Page<KubernetesResourceSnapshotEntity> findBySyncJobIdAndNamespaceAndResourceTypeOrderByResourceTypeAscResourceNameAsc(
            UUID syncJobId, String namespace, String resourceType, Pageable pageable);

    /** KubernetesResourceSnapshotJpaRepository의 countNamespacesBySyncJobId 처리 계약을 정의한다. */
    @Query("""
            select r.namespace, count(r) from KubernetesResourceSnapshotEntity r
            where r.syncJobId = :syncJobId and r.namespace is not null and r.namespace <> ''
            group by r.namespace order by r.namespace asc
            """)
    List<Object[]> countNamespacesBySyncJobId(UUID syncJobId);

    /** KubernetesResourceSnapshotJpaRepository의 countResourceTypesBySyncJobId 처리 계약을 정의한다. */
    @Query("""
            select r.resourceType, count(r) from KubernetesResourceSnapshotEntity r
            where r.syncJobId = :syncJobId
            group by r.resourceType order by r.resourceType asc
            """)
    List<Object[]> countResourceTypesBySyncJobId(UUID syncJobId);

    /** KubernetesResourceSnapshotJpaRepository의 countResourceTypesBySyncJobIdAndNamespace 처리 계약을 정의한다. */
    @Query("""
            select r.resourceType, count(r) from KubernetesResourceSnapshotEntity r
            where r.syncJobId = :syncJobId and r.namespace = :namespace
            group by r.resourceType order by r.resourceType asc
            """)
    List<Object[]> countResourceTypesBySyncJobIdAndNamespace(UUID syncJobId, String namespace);

    /** KubernetesResourceSnapshotJpaRepository의 countProblemsBySyncJobId 처리 계약을 정의한다. */
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

    /** KubernetesResourceSnapshotJpaRepository의 countProblemsBySyncJobIdAndNamespace 처리 계약을 정의한다. */
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

    /** KubernetesResourceSnapshotJpaRepository의 countProblemsBySyncJobIdAndResourceType 처리 계약을 정의한다. */
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

    /** KubernetesResourceSnapshotJpaRepository의 countProblemsBySyncJobIdAndNamespaceAndResourceType 처리 계약을 정의한다. */
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
