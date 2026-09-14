package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.AnalysisAssuranceRepositoryPort;
import io.strato.aiops.application.port.out.AnalysisSessionRepositoryPort;
import io.strato.aiops.application.port.out.OperationsRepositoryPort;
import io.strato.aiops.domain.analysis.AnalysisSession;
import io.strato.aiops.domain.operations.OperationsModels.Incident;
import io.strato.aiops.domain.operations.OperationsModels.IncidentActivity;
import io.strato.aiops.domain.operations.OperationsModels.IncidentState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
class OperationsScorecardQueryServiceTest {

    @Test
    void calculatesLifecycleMetricsWithOneBatchActivityLookup() {
        UUID incidentId = UUID.randomUUID();
        Instant detected = Instant.now().minusSeconds(3_600);
        Incident incident = incident(incidentId, detected);
        AtomicInteger batchLookups = new AtomicInteger();
        OperationsRepositoryPort operations = proxy(OperationsRepositoryPort.class, (method, args) -> switch (method) {
            case "findIncidents" -> List.of(incident);
            case "findIncidentActivitiesByIncidentIds" -> {
                batchLookups.incrementAndGet();
                assertThat(args[0]).isEqualTo(Set.of(incidentId));
                yield List.of(activity(incidentId, IncidentState.ACKNOWLEDGED, detected.plusSeconds(600)),
                        activity(incidentId, IncidentState.RESOLVED, detected.plusSeconds(1_800)));
            }
            default -> throw new UnsupportedOperationException(method);
        });
        AnalysisSessionRepositoryPort analyses = proxy(AnalysisSessionRepositoryPort.class,
                (method, args) -> method.equals("findRecent") ? List.<AnalysisSession>of()
                        : unsupported(method));
        AnalysisAssuranceRepositoryPort assurance = proxy(AnalysisAssuranceRepositoryPort.class,
                (method, args) -> method.equals("findWatchSignalGroups") ? List.of() : unsupported(method));

        var result = new OperationsScorecardQueryService(operations, analyses, assurance).getScorecard();

        assertThat(result.meanTimeToAcknowledgeMinutes()).isEqualTo(10);
        assertThat(result.meanTimeToResolveMinutes()).isEqualTo(30);
        assertThat(result.resolvedIncidents()).isEqualTo(1);
        assertThat(result.hotspots()).singleElement().satisfies(hotspot ->
                assertThat(hotspot.resourceName()).isEqualTo("api"));
        assertThat(batchLookups).hasValue(1);
    }

    private Incident incident(UUID id, Instant detected) {
        return new Incident(id, "fingerprint", UUID.randomUUID(), "cluster", "default", "Pod", "api",
                "PROBE", "HIGH", IncidentState.RESOLVED, "title", "summary", "inspect", 1, 0, null,
                detected, detected.plusSeconds(1_800), "operator");
    }

    private IncidentActivity activity(UUID incidentId, IncidentState state, Instant createdAt) {
        return new IncidentActivity(UUID.randomUUID(), incidentId, "STATE_CHANGED", IncidentState.OPEN, state,
                null, "operator", createdAt);
    }

    private Object unsupported(String method) {
        throw new UnsupportedOperationException(method);
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, PortCall call) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (ignored, method, args) -> call.invoke(method.getName(), args == null ? new Object[0] : args));
    }

    private interface PortCall {
        Object invoke(String method, Object[] args);
    }
}
