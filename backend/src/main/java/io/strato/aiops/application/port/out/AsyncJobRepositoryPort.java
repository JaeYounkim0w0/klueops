package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.job.AsyncJob;

import java.util.List;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AsyncJobRepositoryPort {

    /** AsyncJobRepositoryPort의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    AsyncJob save(AsyncJob job);

    /** AsyncJobRepositoryPort의 saveAndFlush 처리에 필요한 데이터를 생성하거나 저장한다. */
    default AsyncJob saveAndFlush(AsyncJob job) {
        return save(job);
    }

    /** AsyncJobRepositoryPort의 findById 처리 결과를 조회해 반환한다. */
    Optional<AsyncJob> findById(UUID jobId);

    /** AsyncJobRepositoryPort의 findRecent 처리 결과를 조회해 반환한다. */
    List<AsyncJob> findRecent(int limit);

    /** AsyncJobRepositoryPort의 findActiveCreatedBefore 처리 결과를 조회해 반환한다. */
    List<AsyncJob> findActiveCreatedBefore(Instant cutoff);
}
