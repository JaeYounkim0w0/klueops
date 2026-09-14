package io.strato.aiops.runner;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RunnerReplayGuardTest {
    @Test
    void rejectsAnExecutionIdTwice() {
        RunnerReplayGuard guard = new RunnerReplayGuard(Clock.fixed(Instant.parse("2026-09-09T00:00:00Z"), ZoneOffset.UTC), Duration.ofMinutes(5));
        UUID id = UUID.randomUUID();
        assertThat(guard.accept(id)).isTrue();
        assertThat(guard.accept(id)).isFalse();
        assertThat(guard.accept(UUID.randomUUID())).isTrue();
    }
}
