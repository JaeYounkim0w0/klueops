package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AnalysisSessionJpaRepository extends JpaRepository<AnalysisSessionEntity, UUID> {

    /** AnalysisSessionJpaRepository의 findByAsyncJobId 처리 결과를 조회해 반환한다. */
    Optional<AnalysisSessionEntity> findByAsyncJobId(UUID asyncJobId);

    /** AnalysisSessionJpaRepository의 findRunningByScope 처리 결과를 조회해 반환한다. */
    @Query("""
            select session
            from AnalysisSessionEntity session
            where session.clusterId = :clusterId
              and session.status = io.strato.aiops.domain.analysis.AnalysisStatus.RUNNING
              and ((:applicationId is null and session.applicationId is null) or session.applicationId = :applicationId)
              and ((:namespace is null and session.namespace is null) or session.namespace = :namespace)
            order by session.createdAt desc
            """)
    List<AnalysisSessionEntity> findRunningByScope(
            @Param("clusterId") UUID clusterId,
            @Param("applicationId") UUID applicationId,
            @Param("namespace") String namespace,
            Pageable pageable
    );

    /** AnalysisSessionJpaRepository의 findLatestSucceededByScope 처리 결과를 조회해 반환한다. */
    @Query("""
            select session
            from AnalysisSessionEntity session
            where session.clusterId = :clusterId
              and session.status = io.strato.aiops.domain.analysis.AnalysisStatus.SUCCEEDED
              and ((:applicationId is null and session.applicationId is null) or session.applicationId = :applicationId)
              and ((:namespace is null and session.namespace is null) or session.namespace = :namespace)
            order by session.createdAt desc
            """)
    List<AnalysisSessionEntity> findLatestSucceededByScope(
            @Param("clusterId") UUID clusterId,
            @Param("applicationId") UUID applicationId,
            @Param("namespace") String namespace,
            Pageable pageable
    );

    /** AnalysisSessionJpaRepository의 findRecentByScope 처리 결과를 조회해 반환한다. */
    @Query("""
            select session
            from AnalysisSessionEntity session
            where (:clusterId is null or session.clusterId = :clusterId)
              and (:applicationId is null or session.applicationId = :applicationId)
              and (:namespace is null or session.namespace = :namespace)
            order by session.createdAt desc
            """)
    List<AnalysisSessionEntity> findRecentByScope(
            @Param("clusterId") UUID clusterId,
            @Param("applicationId") UUID applicationId,
            @Param("namespace") String namespace,
            Pageable pageable
    );
}
