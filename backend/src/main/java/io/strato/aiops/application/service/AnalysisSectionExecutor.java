package io.strato.aiops.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strato.aiops.application.port.out.AiAnalysisInvalidResponseException;
import io.strato.aiops.application.port.out.AiAnalysisPort;
import io.strato.aiops.domain.analysis.SupportedLocale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

@Service("analysisSectionRunner")
public class AnalysisSectionExecutor {

    private static final int ERROR_MESSAGE_LIMIT = 500;

    private final AiAnalysisPort aiAnalysisPort;
    private final ObjectMapper objectMapper;
    private final Executor executor;
    private final long timeoutMs;
    private final OperationalTelemetry telemetry;

    /** AnalysisSectionExecutor 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AnalysisSectionExecutor(AiAnalysisPort aiAnalysisPort,
                                   ObjectMapper objectMapper,
                                   @Qualifier("analysisSectionExecutor") Executor executor,
                                   @Value("${aiops.ai.analysis.section-timeout-ms:180000}") long timeoutMs,
                                   OperationalTelemetry telemetry) {
        this.aiAnalysisPort = aiAnalysisPort;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.timeoutMs = timeoutMs;
        this.telemetry = telemetry;
    }

    /** AnalysisSectionExecutor의 execute 처리의 핵심 작업 흐름을 실행한다. */
    public CompletableFuture<Result> execute(String sectionName, String instruction, String context) {
        return execute(null, sectionName, instruction, context);
    }

    /** AnalysisSectionExecutor의 execute 처리의 핵심 작업 흐름을 실행한다. */
    public CompletableFuture<Result> execute(UUID tenantId, String sectionName, String instruction, String context) {
        CompletableFuture<Result> future = CompletableFuture.supplyAsync(() -> analyze(tenantId, sectionName, instruction, context),
                executor);
        return future.completeOnTimeout(timeoutFallback(sectionName, context), timeoutMs, TimeUnit.MILLISECONDS)
                .thenApply(result -> {
                    telemetry.recordAnalysisSection(sectionName, telemetryOutcome(result),
                            Duration.ofMillis(result.latencyMs()));
                    return result;
                });
    }

    /** AnalysisSectionExecutor의 executeOrReuse 처리의 핵심 작업 흐름을 실행한다. */
    public CompletableFuture<Result> executeOrReuse(String sectionName, String instruction, String context,
                                                     JsonNode previousResult, SupportedLocale locale) {
        return executeOrReuse(null, sectionName, instruction, context, previousResult, locale);
    }

    /** AnalysisSectionExecutor의 executeOrReuse 처리의 핵심 작업 흐름을 실행한다. */
    public CompletableFuture<Result> executeOrReuse(UUID tenantId, String sectionName, String instruction, String context,
                                                     JsonNode previousResult, SupportedLocale locale) {
        String fingerprint = contextFingerprint(context, locale);
        ObjectNode reused = reusableSection(previousResult, sectionName, fingerprint);
        if (reused != null) {
            reused.put("_sectionName", sectionName);
            reused.put("_sectionSource", "REUSED");
            reused.put("_contextFingerprint", fingerprint);
            return CompletableFuture.completedFuture(new Result(sectionName, reused, context.length(), 0, null));
        }
        return execute(tenantId, sectionName, instruction, context).thenApply(result -> {
            result.result().put("_contextFingerprint", fingerprint);
            return result;
        });
    }

    /** AnalysisSectionExecutor의 analyze 처리의 핵심 작업 흐름을 실행한다. */
    private Result analyze(UUID tenantId, String sectionName, String instruction, String context) {
        long startedNanos = System.nanoTime();
        try {
            ObjectNode result = parseObject(aiAnalysisPort.analyzeSection(tenantId, sectionName, instruction, context));
            requireClusterOperationalContent(sectionName, result);
            result.put("_sectionName", sectionName);
            return new Result(sectionName, result, context.length(), elapsedMillis(startedNanos), null);
        } catch (RuntimeException exception) {
            String message = boundedMessage(exception);
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.put("_sectionError", message);
            fallback.put("_sectionName", sectionName);
            return new Result(sectionName, fallback, context.length(), elapsedMillis(startedNanos), message);
        }
    }

    /** AnalysisSectionExecutor의 timeoutFallback 처리에 필요한 업무 로직을 수행한다. */
    private Result timeoutFallback(String sectionName, String context) {
        String message = "AI section exceeded the configured timeout of " + timeoutMs + "ms";
        ObjectNode fallback = objectMapper.createObjectNode();
        fallback.put("_sectionError", message);
        fallback.put("_sectionName", sectionName);
        return new Result(sectionName, fallback, context.length(), timeoutMs, message);
    }

    /** AnalysisSectionExecutor의 parseObject 처리 데이터를 필요한 표현으로 변환한다. */
    private ObjectNode parseObject(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node instanceof ObjectNode objectNode) {
                return objectNode;
            }
            throw new IllegalArgumentException("JSON object expected");
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse section analysis JSON", exception);
        }
    }

    /** AnalysisSectionExecutor의 requireClusterOperationalContent 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void requireClusterOperationalContent(String sectionName, ObjectNode result) {
        if (!sectionName.startsWith("cluster-")) {
            return;
        }
        List<String> expectedArrays = switch (sectionName) {
            case "cluster-root-cause" -> List.of("/findings", "/rootCauses", "/evidence/resources", "/evidence/events");
            case "cluster-risk-posture" -> List.of(
                    "/performance/bottlenecks", "/performance/improvements", "/scaling/scaleUpCandidates",
                    "/scaling/hpaRecommendations", "/scaling/capacityNotes", "/riskForecast/predictions",
                    "/changeTimeline");
            case "cluster-runbook-operations" -> List.of(
                    "/runbookActions", "/recommendations", "/nextActions", "/verificationCommands",
                    "/operationsGuide/shortTerm", "/operationsGuide/mediumTerm");
            default -> List.of();
        };
        boolean hasOperationalContent = expectedArrays.stream().map(result::at)
                .anyMatch(node -> node.isArray() && !node.isEmpty());
        if (!hasOperationalContent) {
            throw new AiAnalysisInvalidResponseException(
                    "AI cluster section response contained no operational evidence or actions: " + sectionName);
        }
    }

    /** AnalysisSectionExecutor의 reusableSection 처리에 필요한 업무 로직을 수행한다. */
    private ObjectNode reusableSection(JsonNode previousResult, String sectionName, String fingerprint) {
        JsonNode sections = previousResult.path("analysisDiagnostics").path("sections");
        boolean matches = false;
        if (sections.isArray()) {
            for (JsonNode section : sections) {
                if (sectionName.equals(section.path("name").asText())
                        && fingerprint.equals(section.path("contextFingerprint").asText())) {
                    matches = true;
                    break;
                }
            }
        }
        if (!matches) {
            return null;
        }
        ObjectNode result = objectMapper.createObjectNode();
        switch (sectionName) {
            case "root-cause" -> copyFields(previousResult, result,
                    "summary", "severity", "riskScore", "findings", "rootCauses");
            case "log-analysis" -> copyFields(previousResult, result, "logAnalysis");
            case "runbook-operations" -> copyFields(previousResult, result,
                    "runbookActions", "recommendations", "operationsGuide", "nextActions", "verificationCommands");
            default -> {
                return null;
            }
        }
        return result;
    }

    /** AnalysisSectionExecutor의 copyFields 처리에 필요한 업무 로직을 수행한다. */
    private void copyFields(JsonNode source, ObjectNode target, String... fields) {
        for (String field : fields) {
            JsonNode value = source.get(field);
            if (value != null) {
                target.set(field, value.deepCopy());
            }
        }
    }

    /** AnalysisSectionExecutor의 contextFingerprint 처리에 필요한 업무 로직을 수행한다. */
    private String contextFingerprint(String context, SupportedLocale locale) {
        try {
            String stableContext = context
                    .replaceAll("collectedAt=[^\\s]+", "collectedAt=<normalized>")
                    .replaceAll("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z", "<timestamp>");
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("locale=" + locale.tag() + "\n" + stableContext).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to fingerprint analysis context", exception);
        }
    }

    /** AnalysisSectionExecutor의 telemetryOutcome 처리에 필요한 업무 로직을 수행한다. */
    private String telemetryOutcome(Result result) {
        if (result.successful()) {
            return "success";
        }
        String message = result.errorMessage() == null ? "" : result.errorMessage().toLowerCase();
        return message.contains("timeout") || message.contains("timed out") || result.latencyMs() >= timeoutMs
                ? "timeout" : "failure";
    }

    /** AnalysisSectionExecutor의 boundedMessage 처리에 필요한 업무 로직을 수행한다. */
    private String boundedMessage(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() <= ERROR_MESSAGE_LIMIT ? message : message.substring(0, ERROR_MESSAGE_LIMIT);
    }

    /** AnalysisSectionExecutor의 elapsedMillis 처리에 필요한 업무 로직을 수행한다. */
    private long elapsedMillis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    public record Result(String sectionName, ObjectNode result, int contextChars, long latencyMs,
                         String errorMessage) {
        /** Result의 successful 처리에 필요한 업무 로직을 수행한다. */
        public boolean successful() {
            return errorMessage == null && !result.has("_sectionError");
        }

        /** Result의 deterministic 처리에 필요한 업무 로직을 수행한다. */
        public boolean deterministic() {
            return "DETERMINISTIC".equals(result.path("_sectionSource").asText());
        }

        /** Result의 reused 처리에 필요한 업무 로직을 수행한다. */
        public boolean reused() {
            return "REUSED".equals(result.path("_sectionSource").asText());
        }

        /** Result의 status 처리에 필요한 업무 로직을 수행한다. */
        public String status() {
            if (deterministic()) return "DETERMINISTIC";
            if (reused()) return "REUSED";
            return successful() ? "SUCCEEDED" : "FALLBACK";
        }
    }
}
