package io.strato.aiops.application.service;

import io.strato.aiops.domain.operations.OperationsModels.IncidentState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentStateTransitionPolicyTest {

    private final IncidentStateTransitionPolicy policy = new IncidentStateTransitionPolicy();

    @Test
    void acceptsNoOpAndSupportedOperatorTransitions() {
        assertThatCode(() -> policy.validate(IncidentState.OPEN, IncidentState.OPEN)).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate(IncidentState.OPEN, IncidentState.INVESTIGATING)).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate(IncidentState.RESOLVED, IncidentState.REOPENED)).doesNotThrowAnyException();
    }

    @Test
    void rejectsTransitionsThatSkipTheIncidentLifecycle() {
        assertThatThrownBy(() -> policy.validate(IncidentState.MONITORING, IncidentState.ACKNOWLEDGED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid incident state transition");
    }
}
