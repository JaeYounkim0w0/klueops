package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.sync.SyncJob;
import io.strato.aiops.domain.sync.SyncJobStatus;

import java.util.Optional;
import java.util.UUID;

public interface SyncJobRepositoryPort {

    SyncJob save(SyncJob syncJob);

    Optional<SyncJob> findByAsyncJobId(UUID asyncJobId);

    Optional<SyncJob> findLatestByClusterId(UUID clusterId);

    Optional<SyncJob> findLatestByClusterIdAndStatusIn(UUID clusterId, Iterable<SyncJobStatus> statuses);
}
