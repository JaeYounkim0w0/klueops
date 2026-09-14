package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.sync.SyncJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

interface SyncJobJpaRepository extends JpaRepository<SyncJobEntity, UUID> {

    Optional<SyncJobEntity> findByAsyncJobId(UUID asyncJobId);

    Optional<SyncJobEntity> findFirstByClusterIdOrderByCreatedAtDesc(UUID clusterId);

    Optional<SyncJobEntity> findFirstByClusterIdAndStatusInOrderByCreatedAtDesc(UUID clusterId, Collection<SyncJobStatus> statuses);
}
