package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import io.strato.aiops.domain.job.AsyncJobStatus;

interface AsyncJobJpaRepository extends JpaRepository<AsyncJobEntity, UUID> {

    /** AsyncJobJpaRepository의 findAllByOrderByCreatedAtDesc 처리 결과를 조회해 반환한다. */
    List<AsyncJobEntity> findAllByOrderByCreatedAtDesc(org.springframework.data.domain.Pageable pageable);

    /** AsyncJobJpaRepository의 findByStatusInAndCreatedAtBefore 처리 결과를 조회해 반환한다. */
    List<AsyncJobEntity> findByStatusInAndCreatedAtBefore(Collection<AsyncJobStatus> statuses, Instant cutoff);
}
