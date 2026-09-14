package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.CommandExecutionAdmissionPort;
import io.strato.aiops.domain.command.CommandExecutionMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class CommandExecutionCoordinator {
    private final CommandExecutionAdmissionPort admission;
    private final Clock clock;
    private final Limits limits;

    public CommandExecutionCoordinator(
            CommandExecutionAdmissionPort admission,
            Clock clock,
            @Value("${aiops.command-console.limits.user-commands:5}") int userCommands,
            @Value("${aiops.command-console.limits.user-terminals:3}") int userTerminals,
            @Value("${aiops.command-console.limits.cluster-commands:50}") int clusterCommands,
            @Value("${aiops.command-console.limits.cluster-terminals:20}") int clusterTerminals,
            @Value("${aiops.command-console.limits.user-starts-per-minute:30}") int userStartsPerMinute,
            @Value("${aiops.command-console.limits.cluster-starts-per-minute:120}") int clusterStartsPerMinute
    ) {
        this.admission = admission;
        this.clock = clock;
        this.limits = new Limits(positive(userCommands), positive(userTerminals), positive(clusterCommands),
                positive(clusterTerminals), positive(userStartsPerMinute), positive(clusterStartsPerMinute));
    }

    public void acquire(UUID executionId, UUID clusterId, String actor, CommandExecutionMode mode, Duration ttl) {
        Instant now = clock.instant();
        admission.acquire(new CommandExecutionAdmissionPort.AdmissionRequest(executionId, clusterId, actor, mode,
                now, now.plus(ttl), userConcurrency(mode), clusterConcurrency(mode),
                limits.userStartsPerMinute(), limits.clusterStartsPerMinute()));
    }

    public void renew(UUID executionId, Duration ttl) {
        admission.renew(executionId, clock.instant().plus(ttl));
    }

    public void release(UUID executionId) {
        admission.release(executionId);
    }

    public boolean isActive(UUID executionId) {
        return admission.isActive(executionId, clock.instant());
    }

    public Limits limits() {
        return limits;
    }

    private int userConcurrency(CommandExecutionMode mode) {
        return mode == CommandExecutionMode.TERMINAL ? limits.userTerminals() : limits.userCommands();
    }

    private int clusterConcurrency(CommandExecutionMode mode) {
        return mode == CommandExecutionMode.TERMINAL ? limits.clusterTerminals() : limits.clusterCommands();
    }

    private static int positive(int value) {
        return Math.max(1, value);
    }

    public record Limits(
            int userCommands,
            int userTerminals,
            int clusterCommands,
            int clusterTerminals,
            int userStartsPerMinute,
            int clusterStartsPerMinute
    ) {
    }
}
