package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.command.CommandExecutionMode;

import java.time.Instant;
import java.util.UUID;

public interface CommandExecutionAdmissionPort {
    /** CommandExecutionAdmissionPort의 acquire 처리 계약을 정의한다. */
    void acquire(AdmissionRequest request);
    /** CommandExecutionAdmissionPort의 renew 처리 계약을 정의한다. */
    void renew(UUID executionId, Instant expiresAt);
    /** CommandExecutionAdmissionPort의 release 처리 계약을 정의한다. */
    void release(UUID executionId);
    /** CommandExecutionAdmissionPort의 isActive 처리 조건의 충족 여부를 판단한다. */
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
