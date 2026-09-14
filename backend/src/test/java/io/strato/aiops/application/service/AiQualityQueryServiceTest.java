package io.strato.aiops.application.service;

import io.strato.aiops.application.port.out.AnalysisSessionRepositoryPort;
import io.strato.aiops.application.port.out.OperationsRepositoryPort;
import io.strato.aiops.domain.analysis.AnalysisSession;
import io.strato.aiops.domain.analysis.AnalysisStatus;
import io.strato.aiops.domain.operations.OperationsModels.AnalysisFeedback;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
class AiQualityQueryServiceTest {

    @Test
    void calculatesCalibrationWithOneBatchSessionLookup() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UUID thirdId = UUID.randomUUID();
        List<AnalysisFeedback> feedback = List.of(
                feedback(firstId, "CORRECT", "RESOLVED", true),
                feedback(secondId, "PARTIAL", "NO_CHANGE", false),
                feedback(thirdId, "INCORRECT", "NO_CHANGE", false));
        AtomicInteger batchLookups = new AtomicInteger();
        OperationsRepositoryPort operations = proxy(OperationsRepositoryPort.class, (method, args) -> {
            if (method.equals("findAnalysisFeedback")) return feedback;
            throw new UnsupportedOperationException(method);
        });
        AnalysisSessionRepositoryPort analyses = proxy(AnalysisSessionRepositoryPort.class, (method, args) -> {
            if (method.equals("findByIds")) {
                batchLookups.incrementAndGet();
                assertThat(args[0]).isEqualTo(Set.of(firstId, secondId, thirdId));
                return List.of(analysis(firstId, "llama3", "v1"), analysis(secondId, "llama3", "v1"),
                        analysis(thirdId, "llama3", "v1"));
            }
            throw new UnsupportedOperationException(method);
        });

        var result = new AiQualityQueryService(operations, analyses).getCalibration();

        assertThat(result.feedbackCount()).isEqualTo(3);
        assertThat(result.groundTruthCount()).isEqualTo(3);
        assertThat(result.verifiedAccuracyRate()).isEqualTo(66.7);
        assertThat(result.resolutionRate()).isEqualTo(33.3);
        assertThat(result.dangerousSuggestionCount()).isEqualTo(1);
        assertThat(result.profiles()).singleElement().satisfies(profile -> {
            assertThat(profile.model()).isEqualTo("llama3");
            assertThat(profile.promptVersion()).isEqualTo("v1");
            assertThat(profile.feedbackCount()).isEqualTo(3);
        });
        assertThat(batchLookups).hasValue(1);
    }

    private AnalysisFeedback feedback(UUID id, String accuracy, String outcome, boolean dangerous) {
        return new AnalysisFeedback(id, accuracy, outcome, dangerous, null, "root cause", "resolution",
                "Pod", "api", "HIGH", "operator", Instant.parse("2026-09-07T00:00:00Z"));
    }

    private AnalysisSession analysis(UUID id, String model, String promptVersion) {
        return new AnalysisSession(id, UUID.randomUUID(), UUID.randomUUID(), null, "default",
                AnalysisStatus.SUCCEEDED, "ollama", model, promptVersion, "analysis-result.v1",
                "summary", "{}", "operator", Instant.parse("2026-09-07T00:00:00Z"));
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
