package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisResultAssemblerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AnalysisResultAssembler assembler = new AnalysisResultAssembler();

    /** AnalysisResultAssemblerTest의 mergesOnlyFieldsWhoseJsonShapeMatchesTheContract 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void mergesOnlyFieldsWhoseJsonShapeMatchesTheContract() {
        ObjectNode target = mapper.createObjectNode().put("summary", "fallback").put("riskScore", 10);
        ObjectNode source = mapper.createObjectNode();
        source.put("summary", "AI summary");
        source.put("riskScore", 71);
        source.putArray("findings").addObject().put("title", "Pending pod");
        source.putObject("performance").put("summary", "No metrics");

        assembler.mergeText(target, source, "summary");
        assembler.mergeNumber(target, source, "riskScore");
        assembler.mergeArray(target, source, "findings");
        assembler.mergeObject(target, source, "performance");
        assembler.mergeArray(target, source, "performance");

        assertThat(target.path("summary").asText()).isEqualTo("AI summary");
        assertThat(target.path("riskScore").asInt()).isEqualTo(71);
        assertThat(target.path("findings").isArray()).isTrue();
        assertThat(target.path("performance").isObject()).isTrue();
    }

    /** AnalysisResultAssemblerTest의 preservesFallbackValueWhenSourceTextIsBlankOrMissing 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void preservesFallbackValueWhenSourceTextIsBlankOrMissing() {
        ObjectNode target = mapper.createObjectNode().put("summary", "fallback");
        ObjectNode source = mapper.createObjectNode().put("summary", "  ");

        assembler.mergeText(target, source, "summary");
        assembler.mergeObject(target, source, "performance");

        assertThat(target.path("summary").asText()).isEqualTo("fallback");
        assertThat(target.has("performance")).isFalse();
    }

    /** AnalysisResultAssemblerTest의 mapsNamespaceSectionsByNameWithoutDependingOnListOrder 처리 데이터를 필요한 표현으로 변환한다. */
    @Test
    void mapsNamespaceSectionsByNameWithoutDependingOnListOrder() {
        ObjectNode target = mapper.createObjectNode().put("summary", "fallback");
        ObjectNode rootCause = mapper.createObjectNode().put("summary", "AI summary").put("riskScore", 71);
        rootCause.putArray("findings").add("finding");
        ObjectNode performance = mapper.createObjectNode();
        performance.putObject("performance").put("summary", "event based");
        ObjectNode runbook = mapper.createObjectNode();
        runbook.putArray("verificationCommands").add("kubectl get pods");

        assembler.mergeNamespaceSections(target, List.of(
                result("runbook-operations", runbook, null),
                result("performance-scaling", performance, null),
                result("root-cause", rootCause, null)
        ));

        assertThat(target.path("summary").asText()).isEqualTo("AI summary");
        assertThat(target.path("riskScore").asInt()).isEqualTo(71);
        assertThat(target.path("findings")).hasSize(1);
        assertThat(target.path("performance").path("summary").asText()).isEqualTo("event based");
        assertThat(target.path("verificationCommands")).hasSize(1);
        assertThat(target.has("logAnalysis")).isFalse();
    }

    /** AnalysisResultAssemblerTest의 mapsClusterSectionsAndPreservesFallbackForInvalidShapes 처리 데이터를 필요한 표현으로 변환한다. */
    @Test
    void mapsClusterSectionsAndPreservesFallbackForInvalidShapes() {
        ObjectNode target = mapper.createObjectNode().put("severity", "MEDIUM");
        ObjectNode rootCause = mapper.createObjectNode().put("severity", "HIGH");
        ObjectNode posture = mapper.createObjectNode();
        posture.putObject("riskForecast").put("riskLevel", "HIGH");
        posture.put("performance", "invalid shape");
        ObjectNode runbook = mapper.createObjectNode();
        runbook.putObject("operationsGuide").put("summary", "verify first");

        assembler.mergeClusterSections(target, List.of(
                result("cluster-root-cause", rootCause, null),
                result("cluster-risk-posture", posture, null),
                result("cluster-runbook-operations", runbook, null)
        ));

        assertThat(target.path("severity").asText()).isEqualTo("HIGH");
        assertThat(target.path("riskForecast").path("riskLevel").asText()).isEqualTo("HIGH");
        assertThat(target.has("performance")).isFalse();
        assertThat(target.path("operationsGuide").path("summary").asText()).isEqualTo("verify first");
    }

    /** AnalysisResultAssemblerTest의 writesBoundedSectionTelemetryForSuccessReuseAndFallback 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void writesBoundedSectionTelemetryForSuccessReuseAndFallback() {
        ObjectNode target = mapper.createObjectNode();
        ObjectNode succeeded = mapper.createObjectNode().put("_contextFingerprint", "fp-success");
        ObjectNode reused = mapper.createObjectNode()
                .put("_sectionSource", "REUSED")
                .put("_contextFingerprint", "fp-reused");
        ObjectNode fallback = mapper.createObjectNode().put("_sectionError", "provider timeout");
        String longFailure = "x".repeat(600);
        List<AnalysisSectionExecutor.Result> sections = List.of(
                new AnalysisSectionExecutor.Result("root-cause", succeeded, 100, 20, null),
                new AnalysisSectionExecutor.Result("log-analysis", reused, 80, 0, null),
                new AnalysisSectionExecutor.Result("runbook-operations", fallback, 60, 30, "provider timeout")
        );

        assembler.writeSectionTelemetry(target, "namespace-sectioned", null, -1, sections, -3, longFailure);

        assertThat(target.path("sectionStatus")).hasSize(3);
        assertThat(target.path("sectionStatus").get(1).path("status").asText()).isEqualTo("REUSED");
        assertThat(target.path("sectionStatus").get(2).path("status").asText()).isEqualTo("FALLBACK");
        JsonNode diagnostics = target.path("analysisDiagnostics");
        assertThat(diagnostics.path("scope").asText()).isEmpty();
        assertThat(diagnostics.path("totalLatencyMs").asLong()).isZero();
        assertThat(diagnostics.path("totalContextChars").asInt()).isZero();
        assertThat(diagnostics.path("successfulSections").asInt()).isEqualTo(2);
        assertThat(diagnostics.path("failedSections").asInt()).isEqualTo(1);
        assertThat(diagnostics.path("failureReason").asText()).hasSize(503);
        assertThat(diagnostics.path("sections").get(1).path("cacheHit").asBoolean()).isTrue();
    }

    /** AnalysisResultAssemblerTest의 writesIncrementalCountsWithoutTreatingDeterministicSectionsAsAiCalls 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void writesIncrementalCountsWithoutTreatingDeterministicSectionsAsAiCalls() {
        ObjectNode target = mapper.createObjectNode();
        ObjectNode reused = mapper.createObjectNode().put("_sectionSource", "REUSED");
        ObjectNode deterministic = mapper.createObjectNode().put("_sectionSource", "DETERMINISTIC");
        List<AnalysisSectionExecutor.Result> sections = List.of(
                result("root-cause", reused, null),
                result("performance-scaling", deterministic, null),
                result("runbook-operations", mapper.createObjectNode(), null)
        );

        assembler.writeIncrementalMetadata(target, true, sections);

        JsonNode incremental = target.path("incrementalAnalysis");
        assertThat(incremental.path("enabled").asBoolean()).isTrue();
        assertThat(incremental.path("reusedSections").asInt()).isEqualTo(1);
        assertThat(incremental.path("executedAiSections").asInt()).isEqualTo(1);
        assertThat(incremental.path("summary").asText()).contains("1개 섹션");
    }

    /** AnalysisResultAssemblerTest의 keepsSingleCallFallbackTelemetryWithoutSectionStatusContract 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void keepsSingleCallFallbackTelemetryWithoutSectionStatusContract() {
        ObjectNode target = mapper.createObjectNode();
        AnalysisSectionExecutor.Result section = result("full-context", mapper.createObjectNode(), "timeout");

        assembler.writeSingleCallTelemetry(target, "namespace-timeout-fallback", "demo", 50,
                section, 120, "timeout");

        assertThat(target.has("sectionStatus")).isFalse();
        assertThat(target.path("analysisDiagnostics").path("mode").asText())
                .isEqualTo("namespace-timeout-fallback");
        assertThat(target.path("analysisDiagnostics").path("failedSections").asInt()).isEqualTo(1);
    }

    /** AnalysisResultAssemblerTest의 result 처리에 필요한 업무 로직을 수행한다. */
    private AnalysisSectionExecutor.Result result(String name, ObjectNode value, String error) {
        return new AnalysisSectionExecutor.Result(name, value, 10, 5, error);
    }
}
