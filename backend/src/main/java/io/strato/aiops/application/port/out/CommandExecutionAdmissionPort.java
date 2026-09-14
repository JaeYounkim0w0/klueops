package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.command.CommandExecutionMode;

import java.time.Instant;
import java.util.UUID;

public interface CommandExecutionAdmissionPort {
    void acquire(AdmissionRequest request);
    void renew(UUID executionId, Instant expiresAt);
    void release(UUID executionId);
    boolean isActive(UUID executionId, Instant now);

    record AdmissionRequest(
            UUID executionId,
            UUID clusterId,
            String actor,
            CommandExecutionMode mode,
            Instant acquiredAt,
            Instant expiresAt,
            int maximumUserConcurrency,
            int maximumClusterConcurrency,
            int maximumUserStartsPerMinute,
            int maximumClusterStartsPerMinute
    ) {
    }
}
