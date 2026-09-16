package io.strato.aiops.adapter.out.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.function.Consumer;

final class AfterCommitTaskDispatcher {
    private static final Logger log = LoggerFactory.getLogger(AfterCommitTaskDispatcher.class);

    private final TaskExecutor executor;

    /** AfterCommitTaskDispatcher 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    AfterCommitTaskDispatcher(TaskExecutor executor) {
        this.executor = executor;
    }

    /** AfterCommitTaskDispatcher의 dispatch 처리에 필요한 업무 로직을 수행한다. */
    void dispatch(Runnable task, Consumer<RuntimeException> rejectionHandler,
                  Consumer<RuntimeException> executionFailureHandler) {
        Runnable submission = () -> submit(task, rejectionHandler, executionFailureHandler);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // worker가 아직 커밋되지 않은 Job/Application을 읽는 경쟁 조건을 제거한다.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /** 익명 구현체의 afterCommit 처리에 필요한 업무 로직을 수행한다. */
                @Override
                public void afterCommit() {
                    submission.run();
                }
            });
            return;
        }
        submission.run();
    }

    /** AfterCommitTaskDispatcher의 submit 처리에 필요한 업무 로직을 수행한다. */
    private void submit(Runnable task, Consumer<RuntimeException> rejectionHandler,
                        Consumer<RuntimeException> executionFailureHandler) {
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (RuntimeException exception) {
                    safelyHandle(executionFailureHandler, exception, "worker execution failure");
                }
            });
        } catch (RuntimeException exception) {
            safelyHandle(rejectionHandler, exception, "executor rejection");
        }
    }

    /** AfterCommitTaskDispatcher의 safelyHandle 처리에 필요한 업무 로직을 수행한다. */
    private void safelyHandle(Consumer<RuntimeException> handler, RuntimeException exception, String phase) {
        try {
            handler.accept(exception);
        } catch (RuntimeException handlerException) {
            log.error("Application Delivery {} could not be persisted", phase, handlerException);
        }
    }
}
