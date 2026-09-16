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

    /** StaleJobRecoveryServiceTest의 marksOnlyStaleActiveJobsAsTimedOut 처리에 필요한 업무 로직을 수행한다. */
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

        /** InMemoryAsyncJobRepository의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
        @Override
        public AsyncJob save(AsyncJob job) {
            return job;
        }

        /** InMemoryAsyncJobRepository의 findById 처리 결과를 조회해 반환한다. */
        @Override
        public Optional<AsyncJob> findById(UUID jobId) {
            return jobs.stream().filter(job -> job.id().equals(jobId)).findFirst();
        }

        /** InMemoryAsyncJobRepository의 findRecent 처리 결과를 조회해 반환한다. */
        @Override
        public List<AsyncJob> findRecent(int limit) {
            return jobs.stream().limit(limit).toList();
        }

        /** InMemoryAsyncJobRepository의 findActiveCreatedBefore 처리 결과를 조회해 반환한다. */
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
