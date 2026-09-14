package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.job.AsyncJob;

import java.util.List;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AsyncJobRepositoryPort {

    AsyncJob save(AsyncJob job);

    Optional<AsyncJob> findById(UUID jobId);

    List<AsyncJob> findRecent(int limit);

    List<AsyncJob> findActiveCreatedBefore(Instant cutoff);
}
