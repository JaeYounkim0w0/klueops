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

    /** JpaSyncJobRepositoryAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public JpaSyncJobRepositoryAdapter(SyncJobJpaRepository syncJobJpaRepository) {
        this.syncJobJpaRepository = syncJobJpaRepository;
    }

    /** JpaSyncJobRepositoryAdapter의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public SyncJob save(SyncJob syncJob) {
        return syncJobJpaRepository.save(SyncJobEntity.fromDomain(syncJob)).toDomain();
    }

    /** JpaSyncJobRepositoryAdapter의 findByAsyncJobId 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<SyncJob> findByAsyncJobId(UUID asyncJobId) {
        return syncJobJpaRepository.findByAsyncJobId(asyncJobId).map(SyncJobEntity::toDomain);
    }

    /** JpaSyncJobRepositoryAdapter의 findLatestByClusterId 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<SyncJob> findLatestByClusterId(UUID clusterId) {
        return syncJobJpaRepository.findFirstByClusterIdOrderByCreatedAtDesc(clusterId).map(SyncJobEntity::toDomain);
    }

    /** JpaSyncJobRepositoryAdapter의 findLatestByClusterIdAndStatusIn 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<SyncJob> findLatestByClusterIdAndStatusIn(UUID clusterId, Iterable<SyncJobStatus> statuses) {
        List<SyncJobStatus> statusList = new ArrayList<>();
        statuses.forEach(statusList::add);
        return syncJobJpaRepository.findFirstByClusterIdAndStatusInOrderByCreatedAtDesc(clusterId, statusList)
                .map(SyncJobEntity::toDomain);
    }
}
