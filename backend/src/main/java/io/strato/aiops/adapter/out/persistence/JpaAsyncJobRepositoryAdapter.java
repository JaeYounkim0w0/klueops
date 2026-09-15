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

    public JpaAsyncJobRepositoryAdapter(AsyncJobJpaRepository asyncJobJpaRepository) {
        this.asyncJobJpaRepository = asyncJobJpaRepository;
    }

    @Override
    public AsyncJob save(AsyncJob job) {
        return asyncJobJpaRepository.save(AsyncJobEntity.fromDomain(job)).toDomain();
    }

    @Override
    public AsyncJob saveAndFlush(AsyncJob job) {
        return asyncJobJpaRepository.saveAndFlush(AsyncJobEntity.fromDomain(job)).toDomain();
    }

    @Override
    public Optional<AsyncJob> findById(UUID jobId) {
        return asyncJobJpaRepository.findById(jobId).map(AsyncJobEntity::toDomain);
    }

    @Override
    public List<AsyncJob> findRecent(int limit) {
        return asyncJobJpaRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)).stream()
                .map(AsyncJobEntity::toDomain)
                .toList();
    }

    @Override
    public List<AsyncJob> findActiveCreatedBefore(Instant cutoff) {
        return asyncJobJpaRepository.findByStatusInAndCreatedAtBefore(
                        EnumSet.of(AsyncJobStatus.PENDING, AsyncJobStatus.RUNNING), cutoff).stream()
                .map(AsyncJobEntity::toDomain)
                .toList();
    }
}
