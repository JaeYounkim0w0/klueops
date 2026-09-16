package io.strato.aiops.adapter.out.async;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AfterCommitTaskDispatcherTest {

    /** AfterCommitTaskDispatcherTest의 clearTransactionSynchronization 처리 대상과 관련 상태를 안전하게 정리한다. */
    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** AfterCommitTaskDispatcherTest의 transactionCommitHappensBeforeWorkerSubmission 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void transactionCommitHappensBeforeWorkerSubmission() {
        List<Runnable> queue = new ArrayList<>();
        AfterCommitTaskDispatcher dispatcher = new AfterCommitTaskDispatcher(queue::add);
        AtomicInteger executions = new AtomicInteger();
        TransactionSynchronizationManager.initSynchronization();

        dispatcher.dispatch(executions::incrementAndGet, ignored -> { }, ignored -> { });

        assertThat(queue).isEmpty();
        commitSynchronizations();
        assertThat(queue).hasSize(1);
        queue.get(0).run();
        assertThat(executions).hasValue(1);
    }

    /** AfterCommitTaskDispatcherTest의 rolledBackTransactionNeverSubmitsWorker 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void rolledBackTransactionNeverSubmitsWorker() {
        List<Runnable> queue = new ArrayList<>();
        AfterCommitTaskDispatcher dispatcher = new AfterCommitTaskDispatcher(queue::add);
        TransactionSynchronizationManager.initSynchronization();

        dispatcher.dispatch(() -> { }, ignored -> { }, ignored -> { });
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        synchronizations.forEach(item -> item.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(queue).isEmpty();
    }

    /** AfterCommitTaskDispatcherTest의 concurrentRequestsAreQueuedIndependentlyAfterCommit 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void concurrentRequestsAreQueuedIndependentlyAfterCommit() {
        List<Runnable> queue = new ArrayList<>();
        AfterCommitTaskDispatcher dispatcher = new AfterCommitTaskDispatcher(queue::add);
        TransactionSynchronizationManager.initSynchronization();

        for (int index = 0; index < 20; index++) {
            dispatcher.dispatch(() -> { }, ignored -> { }, ignored -> { });
        }

        assertThat(queue).isEmpty();
        commitSynchronizations();
        assertThat(queue).hasSize(20);
    }

    /** AfterCommitTaskDispatcherTest의 executorRejectionAndWorkerCrashUseSeparateFailurePaths 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void executorRejectionAndWorkerCrashUseSeparateFailurePaths() {
        RuntimeException rejection = new IllegalStateException("queue full");
        AtomicReference<RuntimeException> rejected = new AtomicReference<>();
        TaskExecutor rejectingExecutor = task -> { throw rejection; };
        new AfterCommitTaskDispatcher(rejectingExecutor)
                .dispatch(() -> { }, rejected::set, ignored -> { });
        assertThat(rejected).hasValue(rejection);

        List<Runnable> queue = new ArrayList<>();
        RuntimeException crash = new IllegalStateException("worker crashed");
        AtomicReference<RuntimeException> failed = new AtomicReference<>();
        new AfterCommitTaskDispatcher(queue::add)
                .dispatch(() -> { throw crash; }, ignored -> { }, failed::set);
        queue.get(0).run();
        assertThat(failed).hasValue(crash);
    }

    /** AfterCommitTaskDispatcherTest의 commitSynchronizations 처리에 필요한 업무 로직을 수행한다. */
    private void commitSynchronizations() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(item -> item.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    }
}
