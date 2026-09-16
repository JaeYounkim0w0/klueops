package io.strato.aiops.application.service;

import io.strato.aiops.domain.operations.OperationsModels.IncidentState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentStateTransitionPolicyTest {

    private final IncidentStateTransitionPolicy policy = new IncidentStateTransitionPolicy();

    /** IncidentStateTransitionPolicyTest의 acceptsNoOpAndSupportedOperatorTransitions 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void acceptsNoOpAndSupportedOperatorTransitions() {
        assertThatCode(() -> policy.validate(IncidentState.OPEN, IncidentState.OPEN)).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate(IncidentState.OPEN, IncidentState.INVESTIGATING)).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate(IncidentState.RESOLVED, IncidentState.REOPENED)).doesNotThrowAnyException();
    }

    /** IncidentStateTransitionPolicyTest의 rejectsTransitionsThatSkipTheIncidentLifecycle 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void rejectsTransitionsThatSkipTheIncidentLifecycle() {
        assertThatThrownBy(() -> policy.validate(IncidentState.MONITORING, IncidentState.ACKNOWLEDGED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid incident state transition");
    }
}
