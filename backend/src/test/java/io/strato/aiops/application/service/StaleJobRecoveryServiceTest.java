package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.AsyncJobRepositoryPort;
import io.strato.aiops.domain.job.AsyncJob;
import io.strato.aiops.domain.job.AsyncJobStatus;
import io.strato.aiops.domain.job.AsyncJobType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StaleJobRecoveryServiceTest {

    @Test
    void marksOnlyStaleActiveJobsAsTimedOut() {
        Instant now = Instant.parse("2026-07-06T01:00:00Z");
        InMemoryAsyncJobRepository repository = new InMemoryAsyncJobRepository();
        AsyncJob stale = new AsyncJob(UUID.randomUUID(), AsyncJobType.AI_ANALYSIS, AsyncJobStatus.RUNNING,
                now.minusSeconds(1_000), now.minusSeconds(900), null, null, null);
        repository.jobs.add(stale);
        StaleJobRecoveryService service = new StaleJobRecoveryService(repository, 600);

        int recovered = service.recoverStaleJobsAt(now);

        assertThat(recovered).isEqualTo(1);
        assertThat(stale.status()).isEqualTo(AsyncJobStatus.TIMEOUT);
        assertThat(stale.errorMessage()).contains("600 seconds");
        assertThat(repository.cutoff).isEqualTo(now.minusSeconds(600));
    }

    private static final class InMemoryAsyncJobRepository implements AsyncJobRepositoryPort {
        private final List<AsyncJob> jobs = new ArrayList<>();
        private Instant cutoff;

        @Override
        public AsyncJob save(AsyncJob job) {
            return job;
        }

        @Override
        public Optional<AsyncJob> findById(UUID jobId) {
            return jobs.stream().filter(job -> job.id().equals(jobId)).findFirst();
        }

        @Override
        public List<AsyncJob> findRecent(int limit) {
            return jobs.stream().limit(limit).toList();
        }

        @Override
        public List<AsyncJob> findActiveCreatedBefore(Instant cutoff) {
            this.cutoff = cutoff;
            return jobs.stream()
                    .filter(job -> job.createdAt().isBefore(cutoff))
                    .filter(job -> job.status() == AsyncJobStatus.PENDING || job.status() == AsyncJobStatus.RUNNING)
                    .toList();
        }
    }
}
