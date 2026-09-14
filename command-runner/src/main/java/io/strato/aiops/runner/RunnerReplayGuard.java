package io.strato.aiops.runner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
final class RunnerReplayGuard {
    private final ConcurrentHashMap<UUID, Instant> accepted = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration retention;

    @Autowired
    RunnerReplayGuard(@Value("${runner.replay-retention-seconds:900}") long seconds) {
        this(Clock.systemUTC(), Duration.ofSeconds(Math.max(60, seconds)));
    }

    RunnerReplayGuard(Clock clock, Duration retention) {
        this.clock = clock;
        this.retention = retention;
    }

    boolean accept(UUID executionId) {
        Instant now = clock.instant();
        accepted.entrySet().removeIf(entry -> entry.getValue().plus(retention).isBefore(now));
        return accepted.putIfAbsent(executionId, now) == null;
    }
}
