package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.AuditLogRepositoryPort;
import io.strato.aiops.application.port.out.CommandExecutionRepositoryPort;
import io.strato.aiops.domain.audit.AuditLog;
import io.strato.aiops.domain.command.CommandExecution;
import io.strato.aiops.domain.command.CommandStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

@Service
public class CommandExecutionRecoveryService {
    private final CommandExecutionRepositoryPort executions;
    private final CommandExecutionCoordinator coordinator;
    private final CommandEventStream events;
    private final AuditLogRepositoryPort audits;
    private final Clock clock;
    private final Duration minimumAge;

    public CommandExecutionRecoveryService(CommandExecutionRepositoryPort executions,
            CommandExecutionCoordinator coordinator, CommandEventStream events, AuditLogRepositoryPort audits,
            Clock clock, @Value("${aiops.command-console.recovery-minimum-age-seconds:90}") long minimumAgeSeconds) {
        this.executions = executions;
        this.coordinator = coordinator;
        this.events = events;
        this.audits = audits;
        this.clock = clock;
        this.minimumAge = Duration.ofSeconds(Math.max(30, minimumAgeSeconds));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterStartup() {
        recoverOrphans();
    }

    @Scheduled(fixedDelayString = "${aiops.command-console.recovery-interval-ms:30000}")
    public void recoverOrphans() {
        executions.findIncompleteBefore(clock.instant().minus(minimumAge), 200).stream()
                .filter(execution -> !coordinator.isActive(execution.id()))
                .forEach(this::failOrphan);
    }

    private void failOrphan(CommandExecution execution) {
        String reason = "command execution worker lease expired; retry after checking the target state";
        CommandExecution failed = executions.save(execution.completed(CommandStatus.FAILED,
                execution.stdoutText(), reason, -1, elapsed(execution), execution.truncated(), clock.instant()));
        events.status(failed);
        audits.save(AuditLog.create("COMMAND_EXECUTION_RECOVERED_AS_FAILED", "COMMAND_EXECUTION",
                execution.id().toString(), "system", execution.requestId()));
        coordinator.release(execution.id());
    }

    private long elapsed(CommandExecution execution) {
        return Duration.between(execution.startedAt() == null ? execution.createdAt() : execution.startedAt(),
                clock.instant()).toMillis();
    }
}
