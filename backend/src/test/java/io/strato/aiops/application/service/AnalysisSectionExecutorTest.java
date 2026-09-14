package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.strato.aiops.application.port.out.AiAnalysisPort;
import io.strato.aiops.domain.analysis.SupportedLocale;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisSectionExecutorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returnsSuccessfulSectionWithBoundedMetadata() {
        AnalysisSectionExecutor executor = executor((section, instruction, context) ->
                "{\"findings\":[{\"title\":\"probe failed\"}],\"rootCauses\":[]}", Runnable::run, 1000);

        var result = executor.execute("cluster-root-cause", "instruction", "context").join();

        assertThat(result.successful()).isTrue();
        assertThat(result.result().path("_sectionName").asText()).isEqualTo("cluster-root-cause");
        assertThat(result.contextChars()).isEqualTo(7);
    }

    @Test
    void rejectsClusterSectionWithoutOperationalContent() {
        AnalysisSectionExecutor executor = executor((section, instruction, context) -> "{}", Runnable::run, 1000);

        var result = executor.execute("cluster-risk-posture", "instruction", "context").join();

        assertThat(result.successful()).isFalse();
        assertThat(result.errorMessage()).contains("no operational evidence");
        assertThat(result.status()).isEqualTo("FALLBACK");
    }

    @Test
    void returnsTimeoutFallbackWithoutFailingWholeAnalysis() {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            AnalysisSectionExecutor executor = executor((section, instruction, context) -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                return "{}";
            }, worker, 10);

            var result = executor.execute("root-cause", "instruction", "context").join();

            assertThat(result.successful()).isFalse();
            assertThat(result.status()).isEqualTo("FALLBACK");
            assertThat(result.errorMessage()).contains("10ms");
        } finally {
            worker.shutdownNow();
        }
    }

    @Test
    void reusesNamespaceSectionWhenNormalizedContextFingerprintMatches() {
        AtomicInteger calls = new AtomicInteger();
        AnalysisSectionExecutor executor = executor((section, instruction, context) -> {
            calls.incrementAndGet();
            return "{\"summary\":\"first\",\"findings\":[],\"rootCauses\":[]}";
        }, Runnable::run, 1000);
        var first = executor.executeOrReuse("root-cause", "instruction",
                "collectedAt=2026-09-07T00:00:00Z", objectMapper.createObjectNode(), SupportedLocale.ENGLISH).join();
        ObjectNode previous = objectMapper.createObjectNode();
        previous.put("summary", "first");
        previous.putArray("findings");
        previous.putArray("rootCauses");
        previous.putObject("analysisDiagnostics").putArray("sections").addObject()
                .put("name", "root-cause")
                .put("contextFingerprint", first.result().path("_contextFingerprint").asText());

        var reused = executor.executeOrReuse("root-cause", "instruction",
                "collectedAt=2026-09-08T00:00:00Z", previous, SupportedLocale.ENGLISH).join();

        assertThat(calls).hasValue(1);
        assertThat(reused.reused()).isTrue();
        assertThat(reused.status()).isEqualTo("REUSED");
        assertThat(reused.result().path("summary").asText()).isEqualTo("first");
    }

    private AnalysisSectionExecutor executor(SectionAi ai, java.util.concurrent.Executor worker, long timeoutMs) {
        AiAnalysisPort port = new AiAnalysisPort() {
            @Override
            public String analyze(String context) {
                return ai.analyze("full", "", context);
            }

            @Override
            public String analyzeSection(String sectionName, String instruction, String context) {
                return ai.analyze(sectionName, instruction, context);
            }
        };
        return new AnalysisSectionExecutor(port, objectMapper, worker, timeoutMs,
                new OperationalTelemetry(new SimpleMeterRegistry()));
    }

    private interface SectionAi {
        String analyze(String section, String instruction, String context);
    }
}
