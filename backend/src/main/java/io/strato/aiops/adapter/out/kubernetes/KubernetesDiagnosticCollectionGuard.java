package io.strato.aiops.adapter.out.kubernetes;

import io.strato.aiops.application.port.out.KubernetesNamespaceDiagnostics.CollectionStage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntSupplier;

final class KubernetesDiagnosticCollectionGuard {

    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final int maxFailures;
    private final Function<RuntimeException, String> failureDetail;
    private final List<CollectionStage> stages = new ArrayList<>();
    private int failures;
    private int successes;

    /** KubernetesDiagnosticCollectionGuard 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    KubernetesDiagnosticCollectionGuard(int maxFailures, Function<RuntimeException, String> failureDetail) {
        this.maxFailures = Math.max(1, maxFailures);
        this.failureDetail = failureDetail;
    }

    /** KubernetesDiagnosticCollectionGuard의 run 처리의 핵심 작업 흐름을 실행한다. */
    void run(String source, IntSupplier itemCount, Runnable collection) {
        if (failures >= maxFailures) {
            stages.add(new CollectionStage(source, "SKIPPED", 0, 0,
                    "Collection failure budget exhausted after " + failures + " failed sources."));
            return;
        }
        int before = itemCount.getAsInt();
        long startedNanos = System.nanoTime();
        try {
            collection.run();
            successes++;
            stages.add(new CollectionStage(source, "SUCCEEDED",
                    Math.max(0, itemCount.getAsInt() - before), elapsedMillis(startedNanos), null));
        } catch (RuntimeException exception) {
            failures++;
            stages.add(new CollectionStage(source, "FAILED", 0, elapsedMillis(startedNanos),
                    failureDetail.apply(exception)));
        }
    }

    /** KubernetesDiagnosticCollectionGuard의 skip 처리에 필요한 업무 로직을 수행한다. */
    void skip(String source, String detail) {
        stages.add(new CollectionStage(source, "SKIPPED", 0, 0, detail));
    }

    /** KubernetesDiagnosticCollectionGuard의 requireSuccessfulSource 처리 입력과 현재 상태의 유효성을 검증한다. */
    void requireSuccessfulSource() {
        if (successes == 0) {
            String firstFailure = stages.stream()
                    .filter(stage -> "FAILED".equals(stage.status()))
                    .map(CollectionStage::detail)
                    .findFirst()
                    .orElse("No Kubernetes diagnostic source completed.");
            throw new IllegalStateException("All Kubernetes diagnostic collection sources failed: " + firstFailure);
        }
    }

    /** KubernetesDiagnosticCollectionGuard의 stages 처리에 필요한 업무 로직을 수행한다. */
    List<CollectionStage> stages() {
        return List.copyOf(stages);
    }

    /** KubernetesDiagnosticCollectionGuard의 elapsedMillis 처리에 필요한 업무 로직을 수행한다. */
    private long elapsedMillis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / NANOS_PER_MILLI);
    }
}
