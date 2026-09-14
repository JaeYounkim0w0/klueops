package io.strato.aiops.domain.job;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncJobTest {

    @Test
    void pendingJobCanMoveToRunningAndSucceeded() {
        AsyncJob job = AsyncJob.pending(AsyncJobType.CLUSTER_SYNC);

        job.markRunning(Instant.parse("2026-07-06T00:00:00Z"));
        job.markSucceeded(Instant.parse("2026-07-06T00:01:00Z"));

        assertThat(job.status()).isEqualTo(AsyncJobStatus.SUCCEEDED);
        assertThat(job.startedAt()).isEqualTo(Instant.parse("2026-07-06T00:00:00Z"));
        assertThat(job.completedAt()).isEqualTo(Instant.parse("2026-07-06T00:01:00Z"));
    }

    @Test
    void activeJobCanBeMarkedTimedOutButTerminalJobIsPreserved() {
        Instant timeoutAt = Instant.parse("2026-07-06T00:15:00Z");
        AsyncJob running = AsyncJob.pending(AsyncJobType.AI_ANALYSIS);
        running.markRunning(Instant.parse("2026-07-06T00:00:00Z"));

        running.markTimedOut(timeoutAt, "Worker exceeded the configured runtime");

        assertThat(running.status()).isEqualTo(AsyncJobStatus.TIMEOUT);
        assertThat(running.completedAt()).isEqualTo(timeoutAt);
        assertThat(running.errorCode()).isEqualTo("JOB_RUNTIME_EXCEEDED");

        AsyncJob completed = AsyncJob.pending(AsyncJobType.CLUSTER_SYNC);
        completed.markRunning(Instant.parse("2026-07-06T00:00:00Z"));
        completed.markSucceeded(Instant.parse("2026-07-06T00:01:00Z"));
        completed.markTimedOut(timeoutAt, "must not overwrite terminal state");

        assertThat(completed.status()).isEqualTo(AsyncJobStatus.SUCCEEDED);
        assertThat(completed.completedAt()).isEqualTo(Instant.parse("2026-07-06T00:01:00Z"));
    }
}
