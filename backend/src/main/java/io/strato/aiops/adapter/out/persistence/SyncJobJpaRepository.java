package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.sync.SyncJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

interface SyncJobJpaRepository extends JpaRepository<SyncJobEntity, UUID> {

    /** SyncJobJpaRepository의 findByAsyncJobId 처리 결과를 조회해 반환한다. */
    Optional<SyncJobEntity> findByAsyncJobId(UUID asyncJobId);

    /** SyncJobJpaRepository의 findFirstByClusterIdOrderByCreatedAtDesc 처리 결과를 조회해 반환한다. */
    Optional<SyncJobEntity> findFirstByClusterIdOrderByCreatedAtDesc(UUID clusterId);

    /** SyncJobJpaRepository의 findFirstByClusterIdAndStatusInOrderByCreatedAtDesc 처리 결과를 조회해 반환한다. */
    Optional<SyncJobEntity> findFirstByClusterIdAndStatusInOrderByCreatedAtDesc(UUID clusterId, Collection<SyncJobStatus> statuses);
}
