package io.strato.aiops.domain.job;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncJobTest {

    /** AsyncJobTest의 pendingJobCanMoveToRunningAndSucceeded 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void pendingJobCanMoveToRunningAndSucceeded() {
        AsyncJob job = AsyncJob.pending(AsyncJobType.CLUSTER_SYNC);

        job.markRunning(Instant.parse("2026-07-06T00:00:00Z"));
        job.markSucceeded(Instant.parse("2026-07-06T00:01:00Z"));

        assertThat(job.status()).isEqualTo(AsyncJobStatus.SUCCEEDED);
        assertThat(job.startedAt()).isEqualTo(Instant.parse("2026-07-06T00:00:00Z"));
        assertThat(job.completedAt()).isEqualTo(Instant.parse("2026-07-06T00:01:00Z"));
    }

    /** AsyncJobTest의 activeJobCanBeMarkedTimedOutButTerminalJobIsPreserved 처리에 필요한 업무 로직을 수행한다. */
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

    /** AsyncJobTest의 workerFailureTerminatesPendingJobWithoutOverwritingTerminalResult 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void workerFailureTerminatesPendingJobWithoutOverwritingTerminalResult() {
        Instant failedAt = Instant.parse("2026-07-06T00:00:05Z");
        AsyncJob pending = AsyncJob.pending(AsyncJobType.HELM_INSTALL);

        assertThat(pending.markExecutionFailed(failedAt, "HELM_WORKER_FAILED", "worker failed")).isTrue();
        assertThat(pending.status()).isEqualTo(AsyncJobStatus.FAILED);
        assertThat(pending.errorCode()).isEqualTo("HELM_WORKER_FAILED");
        assertThat(pending.completedAt()).isEqualTo(failedAt);

        assertThat(pending.markExecutionFailed(failedAt.plusSeconds(1), "OTHER", "must not overwrite")).isFalse();
        assertThat(pending.errorCode()).isEqualTo("HELM_WORKER_FAILED");
    }
}
