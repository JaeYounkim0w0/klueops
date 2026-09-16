package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.AsyncJobRepositoryPort;
import io.strato.aiops.domain.job.AsyncJob;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import io.strato.aiops.domain.job.AsyncJobStatus;

@Repository
public class JpaAsyncJobRepositoryAdapter implements AsyncJobRepositoryPort {

    private final AsyncJobJpaRepository asyncJobJpaRepository;

    /** JpaAsyncJobRepositoryAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public JpaAsyncJobRepositoryAdapter(AsyncJobJpaRepository asyncJobJpaRepository) {
        this.asyncJobJpaRepository = asyncJobJpaRepository;
    }

    /** JpaAsyncJobRepositoryAdapter의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public AsyncJob save(AsyncJob job) {
        return asyncJobJpaRepository.save(AsyncJobEntity.fromDomain(job)).toDomain();
    }

    /** JpaAsyncJobRepositoryAdapter의 saveAndFlush 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public AsyncJob saveAndFlush(AsyncJob job) {
        return asyncJobJpaRepository.saveAndFlush(AsyncJobEntity.fromDomain(job)).toDomain();
    }

    /** JpaAsyncJobRepositoryAdapter의 findById 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<AsyncJob> findById(UUID jobId) {
        return asyncJobJpaRepository.findById(jobId).map(AsyncJobEntity::toDomain);
    }

    /** JpaAsyncJobRepositoryAdapter의 findRecent 처리 결과를 조회해 반환한다. */
    @Override
    public List<AsyncJob> findRecent(int limit) {
        return asyncJobJpaRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)).stream()
                .map(AsyncJobEntity::toDomain)
                .toList();
    }

    /** JpaAsyncJobRepositoryAdapter의 findActiveCreatedBefore 처리 결과를 조회해 반환한다. */
    @Override
    public List<AsyncJob> findActiveCreatedBefore(Instant cutoff) {
        return asyncJobJpaRepository.findByStatusInAndCreatedAtBefore(
                        EnumSet.of(AsyncJobStatus.PENDING, AsyncJobStatus.RUNNING), cutoff).stream()
                .map(AsyncJobEntity::toDomain)
                .toList();
    }
}
