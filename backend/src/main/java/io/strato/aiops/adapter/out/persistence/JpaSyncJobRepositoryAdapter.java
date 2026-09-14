package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.SyncJobRepositoryPort;
import io.strato.aiops.domain.sync.SyncJob;
import io.strato.aiops.domain.sync.SyncJobStatus;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaSyncJobRepositoryAdapter implements SyncJobRepositoryPort {

    private final SyncJobJpaRepository syncJobJpaRepository;

    public JpaSyncJobRepositoryAdapter(SyncJobJpaRepository syncJobJpaRepository) {
        this.syncJobJpaRepository = syncJobJpaRepository;
    }

    @Override
    public SyncJob save(SyncJob syncJob) {
        return syncJobJpaRepository.save(SyncJobEntity.fromDomain(syncJob)).toDomain();
    }

    @Override
    public Optional<SyncJob> findByAsyncJobId(UUID asyncJobId) {
        return syncJobJpaRepository.findByAsyncJobId(asyncJobId).map(SyncJobEntity::toDomain);
    }

    @Override
    public Optional<SyncJob> findLatestByClusterId(UUID clusterId) {
        return syncJobJpaRepository.findFirstByClusterIdOrderByCreatedAtDesc(clusterId).map(SyncJobEntity::toDomain);
    }

    @Override
    public Optional<SyncJob> findLatestByClusterIdAndStatusIn(UUID clusterId, Iterable<SyncJobStatus> statuses) {
        List<SyncJobStatus> statusList = new ArrayList<>();
        statuses.forEach(statusList::add);
        return syncJobJpaRepository.findFirstByClusterIdAndStatusInOrderByCreatedAtDesc(clusterId, statusList)
                .map(SyncJobEntity::toDomain);
    }
}
