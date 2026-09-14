package io.strato.aiops.application.service;

import io.strato.aiops.domain.operations.OperationsModels.IncidentState;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/** Pure lifecycle policy; persistence, audit, and event publication stay in the control-plane service. */
@Component
public class IncidentStateTransitionPolicy {

    private static final Map<IncidentState, Set<IncidentState>> ALLOWED = Map.of(
            IncidentState.OPEN, Set.of(IncidentState.ACKNOWLEDGED, IncidentState.INVESTIGATING, IncidentState.RESOLVED),
            IncidentState.ACKNOWLEDGED, Set.of(IncidentState.INVESTIGATING, IncidentState.MITIGATING, IncidentState.RESOLVED),
            IncidentState.INVESTIGATING, Set.of(IncidentState.MITIGATING, IncidentState.MONITORING, IncidentState.RESOLVED),
            IncidentState.MITIGATING, Set.of(IncidentState.MONITORING, IncidentState.RESOLVED, IncidentState.INVESTIGATING),
            IncidentState.MONITORING, Set.of(IncidentState.RESOLVED, IncidentState.INVESTIGATING),
            IncidentState.RESOLVED, Set.of(IncidentState.REOPENED),
            IncidentState.REOPENED, Set.of(IncidentState.ACKNOWLEDGED, IncidentState.INVESTIGATING, IncidentState.RESOLVED)
    );

    public void validate(IncidentState from, IncidentState to) {
        if (from == to) {
            return;
        }
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalArgumentException("Invalid incident state transition: " + from + " -> " + to);
        }
    }
}
