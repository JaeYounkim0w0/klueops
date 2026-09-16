package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.CommandExecutionAdmissionPort;
import io.strato.aiops.domain.command.CommandExecutionMode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CommandExecutionCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-09-09T00:00:00Z");

    /** CommandExecutionCoordinatorTest의 appliesSeparateCommandAndTerminalConcurrencyLimits 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void appliesSeparateCommandAndTerminalConcurrencyLimits() {
        RecordingAdmission admission = new RecordingAdmission();
        CommandExecutionCoordinator coordinator = new CommandExecutionCoordinator(admission,
                Clock.fixed(NOW, ZoneOffset.UTC), 5, 3, 50, 20, 30, 120);
        UUID clusterId = UUID.randomUUID();

        coordinator.acquire(UUID.randomUUID(), clusterId, "operator", CommandExecutionMode.COMMAND, Duration.ofMinutes(2));
        assertThat(admission.request.maximumUserConcurrency()).isEqualTo(5);
        assertThat(admission.request.maximumClusterConcurrency()).isEqualTo(50);

        coordinator.acquire(UUID.randomUUID(), clusterId, "operator", CommandExecutionMode.TERMINAL, Duration.ofMinutes(10));
        assertThat(admission.request.maximumUserConcurrency()).isEqualTo(3);
        assertThat(admission.request.maximumClusterConcurrency()).isEqualTo(20);
        assertThat(admission.request.maximumUserStartsPerMinute()).isEqualTo(30);
        assertThat(admission.request.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    }

    /** CommandExecutionCoordinatorTest의 renewsAndReleasesTheSameExecutionLease 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void renewsAndReleasesTheSameExecutionLease() {
        RecordingAdmission admission = new RecordingAdmission();
        CommandExecutionCoordinator coordinator = new CommandExecutionCoordinator(admission,
                Clock.fixed(NOW, ZoneOffset.UTC), 5, 3, 50, 20, 30, 120);
        UUID executionId = UUID.randomUUID();

        coordinator.renew(executionId, Duration.ofMinutes(5));
        coordinator.release(executionId);

        assertThat(admission.renewedExecutionId).isEqualTo(executionId);
        assertThat(admission.renewedUntil).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(admission.releasedExecutionId).isEqualTo(executionId);
    }

    private static final class RecordingAdmission implements CommandExecutionAdmissionPort {
        private AdmissionRequest request;
        private UUID renewedExecutionId;
        private Instant renewedUntil;
        private UUID releasedExecutionId;

        /** RecordingAdmission의 acquire 처리에 필요한 업무 로직을 수행한다. */
        @Override public void acquire(AdmissionRequest value) { request = value; }
        /** RecordingAdmission의 renew 처리에 필요한 업무 로직을 수행한다. */
        @Override public void renew(UUID executionId, Instant expiresAt) {
            renewedExecutionId = executionId;
            renewedUntil = expiresAt;
        }
        /** RecordingAdmission의 release 처리에 필요한 업무 로직을 수행한다. */
        @Override public void release(UUID executionId) { releasedExecutionId = executionId; }
        /** RecordingAdmission의 isActive 처리 조건의 충족 여부를 판단한다. */
        @Override public boolean isActive(UUID executionId, Instant now) { return true; }
    }
}
