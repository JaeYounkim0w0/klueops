package io.strato.aiops.application.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

@Service
public class OperationalTelemetry {

    private static final Set<String> ANALYSIS_SECTIONS = Set.of(
            "root-cause", "log-analysis", "performance-scaling", "risk-timeline", "runbook-operations",
            "cluster-root-cause", "cluster-risk-posture", "cluster-runbook-operations"
    );

    private final MeterRegistry registry;
    private final Map<Key, Accumulator> values = new ConcurrentHashMap<>();

    /** OperationalTelemetry 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public OperationalTelemetry(MeterRegistry registry) {
        this.registry = registry;
    }

    /** OperationalTelemetry의 record 처리에 필요한 업무 로직을 수행한다. */
    public void record(String rawOperation, String rawOutcome, Duration duration) {
        String operation = operation(rawOperation);
        String outcome = outcome(rawOutcome);
        recordKnown(operation, outcome, duration);
    }

    /** OperationalTelemetry의 recordAnalysisSection 처리에 필요한 업무 로직을 수행한다. */
    public void recordAnalysisSection(String rawSection, String rawOutcome, Duration duration) {
        String section = ANALYSIS_SECTIONS.contains(rawSection) ? rawSection : "other-section";
        recordKnown("analysis-" + section, outcome(rawOutcome), duration);
    }

    /** OperationalTelemetry의 recordKnown 처리에 필요한 업무 로직을 수행한다. */
    private void recordKnown(String operation, String outcome, Duration duration) {
        long millis = Math.max(0, duration.toMillis());
        Timer.builder("aiops.operation.duration")
                .description("Bounded latency for product operations")
                .tags("operation", operation, "outcome", outcome)
                .register(registry)
                .record(duration);
        values.computeIfAbsent(new Key(operation, outcome), ignored -> new Accumulator()).add(millis);
    }

    /** OperationalTelemetry의 snapshot 처리에 필요한 업무 로직을 수행한다. */
    public Snapshot snapshot() {
        List<Series> series = values.entrySet().stream()
                .map(entry -> entry.getValue().series(entry.getKey()))
                .sorted(Comparator.comparing(Series::operation).thenComparing(Series::outcome))
                .toList();
        long requests = series.stream().mapToLong(Series::count).sum();
        long failures = series.stream().filter(item -> !"success".equals(item.outcome()))
                .mapToLong(Series::count).sum();
        return new Snapshot("operations-telemetry.v1", Instant.now(), "INSTANCE_SNAPSHOT",
                requests, failures, series);
    }

    /** OperationalTelemetry의 operation 처리에 필요한 업무 로직을 수행한다. */
    static String operation(String value) {
        String path = value == null ? "" : value.toLowerCase();
        if (path.startsWith("/api/analysis")) return "analysis";
        if (path.startsWith("/api/ai-chat")) return "ai-chat";
        if (path.contains("/sync")) return "cluster-sync";
        if (path.contains("/logs") || path.contains("/log")) return "cluster-log";
        if (path.startsWith("/api/clusters")) return "cluster-resource";
        if (path.startsWith("/api/incidents")) return "incident";
        if (path.startsWith("/api/jobs")) return "job";
        if (path.contains("readiness") || path.contains("production-evidence")) return "readiness";
        if (path.startsWith("/api/operations")) return "operations";
        return "other";
    }

    /** OperationalTelemetry의 outcome 처리에 필요한 업무 로직을 수행한다. */
    static String outcome(String value) {
        String normalized = value == null ? "" : value.toLowerCase();
        if (normalized.contains("timeout")) return "timeout";
        if (normalized.startsWith("2") || normalized.startsWith("3") || normalized.equals("success")) return "success";
        if (normalized.startsWith("4") || normalized.contains("client")) return "client-error";
        if (normalized.startsWith("5") || normalized.contains("server") || normalized.contains("fail")) return "server-error";
        return "unknown";
    }

    private record Key(String operation, String outcome) {
    }

    private static final class Accumulator {
        private static final int RESERVOIR_SIZE = 256;
        private final LongAdder count = new LongAdder();
        private final LongAdder totalMs = new LongAdder();
        private final LongAccumulator maxMs = new LongAccumulator(Long::max, 0);
        private final AtomicLong sequence = new AtomicLong();
        private final AtomicLongArray reservoir = new AtomicLongArray(RESERVOIR_SIZE);

        /** Accumulator의 add 처리에 필요한 데이터를 생성하거나 저장한다. */
        void add(long millis) {
            count.increment();
            totalMs.add(millis);
            maxMs.accumulate(millis);
            reservoir.set((int) (sequence.getAndIncrement() % RESERVOIR_SIZE), millis);
        }

        /** Accumulator의 series 처리에 필요한 업무 로직을 수행한다. */
        Series series(Key key) {
            long samples = count.sum();
            return new Series(key.operation(), key.outcome(), samples,
                    samples == 0 ? 0 : Math.round(totalMs.sum() * 1.0 / samples), percentile95(samples), maxMs.get());
        }

        /** Accumulator의 percentile95 처리에 필요한 업무 로직을 수행한다. */
        private long percentile95(long samples) {
            int size = (int) Math.min(samples, RESERVOIR_SIZE);
            if (size == 0) return 0;
            long[] values = new long[size];
            for (int index = 0; index < size; index++) values[index] = reservoir.get(index);
            java.util.Arrays.sort(values);
            return values[Math.max(0, (int) Math.ceil(size * 0.95) - 1)];
        }
    }

    public record Snapshot(String schemaVersion, Instant generatedAt, String retention,
                           long requestCount, long failureCount, List<Series> series) {
    }

    public record Series(String operation, String outcome, long count, long averageMs, long p95Ms, long maxMs) {
    }
}
