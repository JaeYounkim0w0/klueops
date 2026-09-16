package io.strato.aiops.application.service;

import io.strato.aiops.domain.application.ApplicationStatus;
import io.strato.aiops.domain.application.ManagedApplication;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationOperationConcurrencyPolicyTest {
    private final ApplicationOperationConcurrencyPolicy policy = new ApplicationOperationConcurrencyPolicy();

    /** ApplicationOperationConcurrencyPolicyTest의 activeLifecycleStatusesRejectAnotherMutation 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void activeLifecycleStatusesRejectAnotherMutation() {
        List.of(ApplicationStatus.DEPLOY_REQUESTED, ApplicationStatus.DEPLOYING, ApplicationStatus.UPGRADING,
                        ApplicationStatus.ROLLING_BACK, ApplicationStatus.UNINSTALLING)
                .forEach(status -> assertThatThrownBy(() -> policy.requireOperationAvailable(application(status)))
                        .isInstanceOf(ApplicationOperationConflictException.class)
                        .hasMessageContaining("already active"));
    }

    /** ApplicationOperationConcurrencyPolicyTest의 terminalAndStableStatusesAllowNextMutation 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void terminalAndStableStatusesAllowNextMutation() {
        List.of(ApplicationStatus.RUNNING, ApplicationStatus.RUNNING_ENDPOINT_DEGRADED,
                        ApplicationStatus.DEGRADED, ApplicationStatus.FAILED)
                .forEach(status -> assertThatCode(() -> policy.requireOperationAvailable(application(status)))
                        .doesNotThrowAnyException());
    }

    /** ApplicationOperationConcurrencyPolicyTest의 application 처리에 필요한 업무 로직을 수행한다. */
    private ManagedApplication application(ApplicationStatus status) {
        return ManagedApplication.helmChart(UUID.randomUUID(), "team-a", "postgres", "postgres", "postgres", "actor")
                .withStatus(status, null, null);
    }
}
