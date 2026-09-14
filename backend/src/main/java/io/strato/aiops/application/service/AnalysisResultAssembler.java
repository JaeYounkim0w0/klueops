package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
final class AnalysisResultAssembler {

    void mergeNamespaceSections(ObjectNode target, List<AnalysisSectionExecutor.Result> sections) {
        mergeSection(target, section(sections, "root-cause"),
                List.of("findings", "rootCauses"), List.of(), List.of("summary", "severity"), List.of("riskScore"));
        mergeSection(target, section(sections, "log-analysis"),
                List.of("logAnalysis"), List.of(), List.of(), List.of());
        mergeSection(target, section(sections, "performance-scaling"),
                List.of(), List.of("performance", "scaling"), List.of(), List.of());
        mergeSection(target, section(sections, "risk-timeline"),
                List.of("changeTimeline"), List.of("riskForecast"), List.of(), List.of());
        mergeSection(target, section(sections, "runbook-operations"),
                List.of("runbookActions", "recommendations", "nextActions", "verificationCommands"),
                List.of("operationsGuide"), List.of(), List.of());
    }

    void mergeClusterSections(ObjectNode target, List<AnalysisSectionExecutor.Result> sections) {
        mergeSection(target, section(sections, "cluster-root-cause"),
                List.of("findings", "rootCauses"), List.of(), List.of("summary", "severity"), List.of("riskScore"));
        mergeSection(target, section(sections, "cluster-risk-posture"),
                List.of("changeTimeline"), List.of("performance", "scaling", "riskForecast"), List.of(), List.of());
        mergeSection(target, section(sections, "cluster-runbook-operations"),
                List.of("runbookActions", "recommendations", "nextActions", "verificationCommands"),
                List.of("operationsGuide"), List.of(), List.of());
    }

    void writeSectionTelemetry(ObjectNode target, String mode, String scope, long totalLatencyMs,
                               List<AnalysisSectionExecutor.Result> sections, int totalContextChars,
                               String failureReason) {
        ArrayNode statuses = target.putArray("sectionStatus");
        sections.forEach(section -> {
            ObjectNode status = statuses.addObject();
            status.put("section", section.sectionName());
            status.put("status", section.status());
            status.put("contextChars", section.contextChars());
            status.put("latencyMs", section.latencyMs());
            if (section.result().has("_sectionError")) {
                status.put("error", section.result().path("_sectionError").asText());
            }
        });

        ObjectNode diagnostics = target.putObject("analysisDiagnostics");
        diagnostics.put("mode", mode);
        diagnostics.put("scope", valueOrBlank(scope));
        diagnostics.put("totalLatencyMs", Math.max(0, totalLatencyMs));
        diagnostics.put("totalContextChars", Math.max(0, totalContextChars));
        diagnostics.put("sectionCount", sections.size());
        diagnostics.put("successfulSections", sections.stream().filter(AnalysisSectionExecutor.Result::successful).count());
        diagnostics.put("failedSections", sections.stream().filter(section -> !section.successful()).count());
        if (failureReason != null && !failureReason.isBlank()) {
            diagnostics.put("failureReason", truncate(failureReason, 500));
        }
        ArrayNode sectionNodes = diagnostics.putArray("sections");
        sections.forEach(section -> {
            ObjectNode sectionNode = sectionNodes.addObject();
            sectionNode.put("name", section.sectionName());
            sectionNode.put("status", section.status());
            sectionNode.put("contextChars", section.contextChars());
            sectionNode.put("latencyMs", section.latencyMs());
            sectionNode.put("contextFingerprint", section.result().path("_contextFingerprint").asText(""));
            sectionNode.put("cacheHit", section.reused());
            if (section.errorMessage() != null && !section.errorMessage().isBlank()) {
                sectionNode.put("error", section.errorMessage());
            }
        });
    }

    void writeIncrementalMetadata(ObjectNode target, boolean enabled,
                                  List<AnalysisSectionExecutor.Result> sections) {
        long reusedSections = sections.stream().filter(AnalysisSectionExecutor.Result::reused).count();
        long aiSectionCount = sections.stream().filter(section -> !section.deterministic()).count();
        ObjectNode incremental = target.putObject("incrementalAnalysis");
        incremental.put("enabled", enabled);
        incremental.put("reusedSections", reusedSections);
        incremental.put("executedAiSections", aiSectionCount - reusedSections);
        incremental.put("summary", reusedSections == 0
                ? "변경된 근거를 기준으로 AI 섹션을 새로 분석했습니다."
                : reusedSections + "개 섹션은 동일 근거 fingerprint를 확인해 이전 검증 결과를 재사용했습니다.");
    }

    void writeSingleCallTelemetry(ObjectNode target, String mode, String scope, long totalLatencyMs,
                                  AnalysisSectionExecutor.Result section, int totalContextChars,
                                  String failureReason) {
        writeSectionTelemetry(target, mode, scope, totalLatencyMs, List.of(section), totalContextChars, failureReason);
        target.remove("sectionStatus");
    }

    private void mergeSection(ObjectNode target, ObjectNode source, List<String> arrays, List<String> objects,
                              List<String> texts, List<String> numbers) {
        arrays.forEach(field -> mergeArray(target, source, field));
        objects.forEach(field -> mergeObject(target, source, field));
        texts.forEach(field -> mergeText(target, source, field));
        numbers.forEach(field -> mergeNumber(target, source, field));
    }

    private ObjectNode section(List<AnalysisSectionExecutor.Result> sections, String sectionName) {
        return sections.stream()
                .filter(section -> sectionName.equals(section.sectionName()))
                .findFirst()
                .map(AnalysisSectionExecutor.Result::result)
                .orElseGet(JsonNodeFactory.instance::objectNode);
    }

    void mergeArray(ObjectNode target, ObjectNode source, String fieldName) {
        JsonNode value = source.get(fieldName);
        if (value != null && value.isArray()) {
            target.set(fieldName, value);
        }
    }

    void mergeObject(ObjectNode target, ObjectNode source, String fieldName) {
        JsonNode value = source.get(fieldName);
        if (value != null && value.isObject()) {
            target.set(fieldName, value);
        }
    }

    void mergeText(ObjectNode target, ObjectNode source, String fieldName) {
        JsonNode value = source.get(fieldName);
        if (value != null && value.isTextual() && !value.asText().isBlank()) {
            target.put(fieldName, value.asText());
        }
    }

    void mergeNumber(ObjectNode target, ObjectNode source, String fieldName) {
        JsonNode value = source.get(fieldName);
        if (value != null && value.isNumber()) {
            target.put(fieldName, value.asInt());
        }
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

    private String valueOrBlank(String value) {
        return value == null ? "" : value;
    }
}
