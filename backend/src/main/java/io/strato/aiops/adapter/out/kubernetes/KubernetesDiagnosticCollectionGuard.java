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

    KubernetesDiagnosticCollectionGuard(int maxFailures, Function<RuntimeException, String> failureDetail) {
        this.maxFailures = Math.max(1, maxFailures);
        this.failureDetail = failureDetail;
    }

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

    void skip(String source, String detail) {
        stages.add(new CollectionStage(source, "SKIPPED", 0, 0, detail));
    }

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

    List<CollectionStage> stages() {
        return List.copyOf(stages);
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / NANOS_PER_MILLI);
    }
}
