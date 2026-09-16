package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.sync.SyncJob;
import io.strato.aiops.domain.sync.SyncJobStatus;

import java.util.Optional;
import java.util.UUID;

public interface SyncJobRepositoryPort {

    /** SyncJobRepositoryPort의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    SyncJob save(SyncJob syncJob);

    /** SyncJobRepositoryPort의 findByAsyncJobId 처리 결과를 조회해 반환한다. */
    Optional<SyncJob> findByAsyncJobId(UUID asyncJobId);

    /** SyncJobRepositoryPort의 findLatestByClusterId 처리 결과를 조회해 반환한다. */
    Optional<SyncJob> findLatestByClusterId(UUID clusterId);

    /** SyncJobRepositoryPort의 findLatestByClusterIdAndStatusIn 처리 결과를 조회해 반환한다. */
    Optional<SyncJob> findLatestByClusterIdAndStatusIn(UUID clusterId, Iterable<SyncJobStatus> statuses);
}
