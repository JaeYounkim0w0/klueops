package io.strato.aiops.adapter.out.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.AiAnalysisPort;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class OllamaAiAnalysisAdapter implements AiAnalysisPort {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final double temperature;

    public OllamaAiAnalysisAdapter(ChatClient.Builder chatClientBuilder,
                                   ObjectMapper objectMapper,
                                   @Value("${aiops.ai.model:qwen2.5-coder:7b}") String model,
                                   @Value("${aiops.ai.temperature:0.1}") double temperature) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.model = model;
        this.temperature = temperature;
    }

    @Override
    public String analyze(String sanitizedKubernetesContext) {
        String systemPrompt = """
                You are the KlueOps RCA Assistant for evidence-guided Kubernetes operations.
                Return only JSON compatible with analysis-result.v1.
                Do not include markdown fences.
                Use only the sanitized Kubernetes context provided by the user.
                Honor the analysisScope from the context.
                For namespace-live-diagnostics analyze only that namespace. For cluster-live-inventory analyze the
                complete cluster across namespaces and do not return namespace-specific fields outside the JSON shape.
                Analyze Kubernetes resources, events, pod logs, scaling signals, and operational risks for that scope.
                Identify probable root causes only when supported by evidence from resources, events, or logs.
                Include practical remediation steps, verification commands, performance guidance, scaling guidance,
                and cluster operation consultation notes.
                Distinguish facts from recommendations. Never claim that you executed kubectl or changed the cluster.
                Quality rules:
                - Every finding, root cause, recommendation, and risk score must cite concrete evidence from the context.
                - Do not say all pods or services are healthy unless the context includes ready pod/container signals and ready endpoints.
                - If CPU, memory, latency, saturation, or traffic metrics are absent, say metric-based performance is not available.
                - Do not infer debug mode is enabled when a log explicitly says "Debug mode: off".
                - A Flask "development server" warning means the serving runtime is not production WSGI; phrase it exactly that way.
                - If evidence is weak or partial, lower confidence and use "needs verification" instead of a definitive root cause.
                - Prefer kubectl get/describe/logs verification commands before kubectl edit/apply/delete commands.
                - Keep recommendations concrete: name the resource, expected signal, action, reason, and verification.
                - Even when no issue is detected, include at least one evidence item or read-only verification command
                  and a short-term operationsGuide entry. Never return every operational array empty.
                Use this JSON shape:
                {
                  "schemaVersion": "analysis-result.v1",
                  "summary": "...",
                  "severity": "INFO|LOW|MEDIUM|HIGH|CRITICAL",
                  "confidence": 0.0,
                  "riskScore": 0,
                  "findings": [
                    {
                      "title": "...",
                      "resourceKind": "...",
                      "resourceName": "...",
                      "namespace": "...",
                      "evidence": ["..."],
                      "impact": "..."
                    }
                  ],
                  "rootCauses": [
                    {
                      "cause": "...",
                      "confidence": 0.0,
                      "evidence": ["..."]
                    }
                  ],
                  "logAnalysis": [
                    {
                      "podName": "...",
                      "containerName": "...",
                      "signal": "ERROR|WARN|INFO|UNKNOWN",
                      "pattern": "...",
                      "evidence": ["..."],
                      "interpretation": "..."
                    }
                  ],
                  "performance": {
                    "summary": "...",
                    "bottlenecks": [
                      {
                        "resourceKind": "...",
                        "resourceName": "...",
                        "signal": "...",
                        "recommendation": "..."
                      }
                    ],
                    "improvements": ["..."]
                  },
                  "scaling": {
                    "summary": "...",
                    "scaleUpCandidates": [
                      {
                        "resourceKind": "...",
                        "resourceName": "...",
                        "currentSignal": "...",
                        "recommendation": "..."
                      }
                    ],
                    "hpaRecommendations": ["..."],
                    "capacityNotes": ["..."]
                  },
                  "riskForecast": {
                    "summary": "...",
                    "predictions": [
                      {
                        "category": "availability|stability|traffic|storage|scaling|security|operability",
                        "severity": "LOW|MEDIUM|HIGH|CRITICAL",
                        "probability": 0,
                        "horizon": "...",
                        "resourceKind": "...",
                        "resourceName": "...",
                        "signal": "...",
                        "impact": "...",
                        "recommendation": "...",
                        "evidence": ["..."],
                        "verificationCommand": "kubectl ..."
                      }
                    ]
                  },
                  "changeTimeline": [
                    {
                      "occurredAt": "...",
                      "severity": "INFO|WARN|HIGH|CRITICAL",
                      "category": "rollout-change|degradation|log-signal|current-state",
                      "resourceKind": "...",
                      "resourceName": "...",
                      "title": "...",
                      "detail": "...",
                      "suspectedChange": "...",
                      "recommendation": "..."
                    }
                  ],
                  "runbookActions": [
                    {
                      "priority": "P0|P1|P2|P3",
                      "category": "verification|diagnosis|safe-action|destructive",
                      "title": "...",
                      "targetKind": "...",
                      "targetName": "...",
                      "reason": "...",
                      "why": "...",
                      "command": "kubectl ...",
                      "commandType": "read-only|safe-change|destructive",
                      "destructive": false
                    }
                  ],
                  "recommendations": [
                    {
                      "priority": "P0|P1|P2|P3",
                      "action": "...",
                      "reason": "...",
                      "commands": ["kubectl ..."]
                    }
                  ],
                  "operationsGuide": {
                    "summary": "...",
                    "shortTerm": ["..."],
                    "mediumTerm": ["..."],
                    "questionsForOperator": ["..."]
                  },
                  "nextActions": [
                    {
                      "priority": "P0|P1|P2|P3",
                      "action": "...",
                      "ownerHint": "platform|application|network|storage|security",
                      "verification": "..."
                    }
                  ],
                  "verificationCommands": ["kubectl ..."],
                  "evidence": {
                    "resources": [],
                    "events": [],
                    "logs": []
                  }
                }
                """;
        String context = sanitizedKubernetesContext == null ? "" : sanitizedKubernetesContext;
        try {
            String content = callJson(systemPrompt, context);
            return normalizeAndValidateJsonResponse(content);
        } catch (OllamaAiException exception) {
            throw exception;
        } catch (AiAnalysisResponseFormatException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OllamaAiException("Ollama analysis request failed: " + exception.getMessage(), exception);
        }
    }

    @Override
    public String analyzeSection(String sectionName, String sectionInstruction, String sanitizedKubernetesContext) {
        String systemPrompt = """
                You are a KlueOps section analyst for evidence-guided Kubernetes operations.
                Return only a compact JSON object. Do not include markdown fences.
                Use only the sanitized Kubernetes context provided by the user.
                Analyze only the requested section. Do not generate unrelated sections.
                Every conclusion must cite evidence from the provided context.
                If evidence is weak or metrics are absent, say verification is needed.

                Section name: %s
                Section instruction:
                %s
                """.formatted(sectionName, sectionInstruction);
        String context = sanitizedKubernetesContext == null ? "" : sanitizedKubernetesContext;
        try {
            return normalizeSectionJsonResponse(callJson(systemPrompt, context));
        } catch (OllamaAiException exception) {
            throw exception;
        } catch (AiAnalysisResponseFormatException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OllamaAiException("Ollama analysis section request failed: " + exception.getMessage(), exception);
        }
    }

    private String callJson(String systemPrompt, String context) {
        ChatResponse response = chatClient.prompt()
                .system(systemPrompt)
                .user(context)
                .options(OllamaChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .format("json")
                        .build())
                .call()
                .chatResponse();
        return extractContent(response);
    }

    private String extractContent(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null
                || response.getResult().getOutput().getText() == null
                || response.getResult().getOutput().getText().isBlank()) {
            throw new OllamaAiException("Ollama analysis response was empty");
        }
        return response.getResult().getOutput().getText();
    }

    private String normalizeAndValidateJsonResponse(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        String normalized = trimmed.trim();
        if (normalized.isBlank()) {
            throw new OllamaAiException("Ollama analysis response was empty");
        }
        try {
            JsonNode root = repairAnalysisShape(objectMapper.readTree(normalized));
            validateRequiredAnalysisShape(root);
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new AiAnalysisResponseFormatException("Ollama analysis response was not valid JSON: "
                    + exception.getOriginalMessage().toLowerCase(Locale.ROOT), exception);
        }
    }

    private String normalizeSectionJsonResponse(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        String normalized = trimmed.trim();
        if (normalized.isBlank()) {
            throw new OllamaAiException("Ollama analysis section response was empty");
        }
        try {
            JsonNode root = objectMapper.readTree(normalized);
            if (!root.isObject()) {
                throw new AiAnalysisResponseFormatException("Ollama analysis section response must be a JSON object");
            }
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new AiAnalysisResponseFormatException("Ollama analysis section response was not valid JSON: "
                    + exception.getOriginalMessage().toLowerCase(Locale.ROOT), exception);
        }
    }

    private JsonNode repairAnalysisShape(JsonNode root) {
        if (!root.isObject()) {
            return root;
        }
        ObjectNode objectNode = (ObjectNode) root;
        if (!objectNode.hasNonNull("schemaVersion") || !objectNode.get("schemaVersion").isTextual()
                || objectNode.get("schemaVersion").asText().isBlank()) {
            objectNode.put("schemaVersion", "analysis-result.v1");
        }
        putMissingArray(objectNode, "findings");
        putMissingArray(objectNode, "rootCauses");
        putMissingArray(objectNode, "logAnalysis");
        putMissingArray(objectNode, "recommendations");
        putMissingArray(objectNode, "changeTimeline");
        putMissingArray(objectNode, "runbookActions");
        putMissingArray(objectNode, "nextActions");
        putMissingArray(objectNode, "verificationCommands");
        putMissingObject(objectNode, "performance");
        putMissingObject(objectNode, "scaling");
        putMissingObject(objectNode, "riskForecast");
        putMissingObject(objectNode, "operationsGuide");
        putMissingObject(objectNode, "evidence");
        ensurePerformanceShape(objectNode);
        ensureScalingShape(objectNode);
        ensureRiskForecastShape(objectNode);
        ensureOperationsGuideShape(objectNode);
        ensureEvidenceShape(objectNode);
        ensureTopLevelShape(objectNode);
        return objectNode;
    }

    private void ensureTopLevelShape(ObjectNode root) {
        if (!hasText(root, "summary")) {
            String summary = firstText(root.path("findings"), "title");
            if (summary.isBlank()) {
                summary = firstText(root.path("rootCauses"), "cause");
            }
            if (summary.isBlank()) {
                summary = root.path("operationsGuide").path("summary").asText("");
            }
            root.put("summary", summary.isBlank()
                    ? "AI analysis returned structured evidence without a summary. Review findings and verification commands."
                    : summary);
        }
        if (!hasText(root, "severity")) {
            root.put("severity", severityFromRiskScore(root.path("riskScore").asInt(0)));
        }
        if (!root.has("confidence") || !root.get("confidence").isNumber()) {
            root.put("confidence", 0.35);
        }
        if (!root.has("riskScore") || !root.get("riskScore").isNumber()) {
            root.put("riskScore", 0);
        }
    }

    private boolean hasText(ObjectNode root, String fieldName) {
        return root.hasNonNull(fieldName) && root.get(fieldName).isTextual()
                && !root.get(fieldName).asText().isBlank();
    }

    private String firstText(JsonNode array, String fieldName) {
        if (!array.isArray()) {
            return "";
        }
        for (JsonNode item : array) {
            String value = item.path(fieldName).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String severityFromRiskScore(int riskScore) {
        if (riskScore >= 80) {
            return "CRITICAL";
        }
        if (riskScore >= 60) {
            return "HIGH";
        }
        if (riskScore >= 30) {
            return "MEDIUM";
        }
        if (riskScore > 0) {
            return "LOW";
        }
        return "INFO";
    }

    private void putMissingArray(ObjectNode objectNode, String fieldName) {
        if (!objectNode.has(fieldName) || objectNode.get(fieldName).isNull() || !objectNode.get(fieldName).isArray()) {
            objectNode.putArray(fieldName);
        }
    }

    private void putMissingObject(ObjectNode objectNode, String fieldName) {
        if (!objectNode.has(fieldName) || objectNode.get(fieldName).isNull() || !objectNode.get(fieldName).isObject()) {
            objectNode.putObject(fieldName);
        }
    }

    private void ensurePerformanceShape(ObjectNode root) {
        ObjectNode performance = objectNode(root, "performance");
        if (!performance.hasNonNull("summary")) {
            performance.put("summary", "Metric-based performance data is not available in the provided context.");
        }
        putMissingArray(performance, "bottlenecks");
        putMissingArray(performance, "improvements");
    }

    private void ensureScalingShape(ObjectNode root) {
        ObjectNode scaling = objectNode(root, "scaling");
        if (!scaling.hasNonNull("summary")) {
            scaling.put("summary", "Scaling assessment needs verification with the provided Kubernetes spec/status signals.");
        }
        putMissingArray(scaling, "scaleUpCandidates");
        putMissingArray(scaling, "hpaRecommendations");
        putMissingArray(scaling, "capacityNotes");
    }

    private void ensureRiskForecastShape(ObjectNode root) {
        ObjectNode riskForecast = objectNode(root, "riskForecast");
        if (!riskForecast.hasNonNull("summary")) {
            riskForecast.put("summary", "Risk forecast is based only on the provided Kubernetes API signals.");
        }
        putMissingArray(riskForecast, "predictions");
    }

    private void ensureOperationsGuideShape(ObjectNode root) {
        ObjectNode operationsGuide = objectNode(root, "operationsGuide");
        if (!operationsGuide.hasNonNull("summary")) {
            operationsGuide.put("summary", "Review findings and verification commands before taking action.");
        }
        putMissingArray(operationsGuide, "shortTerm");
        putMissingArray(operationsGuide, "mediumTerm");
        putMissingArray(operationsGuide, "questionsForOperator");
    }

    private void ensureEvidenceShape(ObjectNode root) {
        ObjectNode evidence = objectNode(root, "evidence");
        putMissingArray(evidence, "resources");
        putMissingArray(evidence, "events");
        putMissingArray(evidence, "logs");
    }

    private ObjectNode objectNode(ObjectNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        if (node instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode replacement = objectMapper.createObjectNode();
        root.set(fieldName, replacement);
        return replacement;
    }

    private void validateRequiredAnalysisShape(JsonNode root) {
        if (!root.isObject()) {
            throw new AiAnalysisResponseFormatException("Ollama analysis response must be a JSON object");
        }
        requireText(root, "schemaVersion");
        requireText(root, "summary");
        requireText(root, "severity");
        requireArray(root, "findings");
        requireArray(root, "rootCauses");
        requireArray(root, "recommendations");
        requireArray(root, "changeTimeline");
        requireArray(root, "runbookActions");
        requireArray(root, "verificationCommands");
        requireObject(root, "evidence");
        requireArray(root, "logAnalysis");
        requireObject(root, "performance");
        requireObject(root, "scaling");
        requireObject(root, "riskForecast");
        requireObject(root, "operationsGuide");
        requireArray(root, "nextActions");
    }

    private void requireText(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new AiAnalysisResponseFormatException("Ollama analysis response missing required text field: " + fieldName);
        }
    }

    private void requireArray(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isArray()) {
            throw new AiAnalysisResponseFormatException("Ollama analysis response missing required array field: " + fieldName);
        }
    }

    private void requireObject(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isObject()) {
            throw new AiAnalysisResponseFormatException("Ollama analysis response missing required object field: " + fieldName);
        }
    }
}
