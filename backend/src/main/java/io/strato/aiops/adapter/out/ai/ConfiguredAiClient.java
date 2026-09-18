package io.strato.aiops.adapter.out.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strato.aiops.application.port.out.AiProviderConfigurationRepositoryPort;
import io.strato.aiops.application.port.out.SecretCryptoPort;
import io.strato.aiops.domain.ai.AiProviderProfile;
import io.strato.aiops.domain.ai.TenantAiRoutingPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
class ConfiguredAiClient {
    private static final Logger log = LoggerFactory.getLogger(ConfiguredAiClient.class);
    private static final int MAX_RESPONSE_CHARS = 4_000_000;
    private static final long MAX_REQUEST_TIMEOUT_MS = 600_000L;
    private final AiProviderConfigurationRepositoryPort configurations;
    private final SecretCryptoPort crypto;
    private final ObjectMapper mapper;
    private final int maximumOllamaContextTokens;
    private final Duration requestTimeout;
    private final Duration helmValuesRequestTimeout;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    /** ConfiguredAiClient 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    ConfiguredAiClient(AiProviderConfigurationRepositoryPort configurations, SecretCryptoPort crypto,
                       ObjectMapper mapper,
                       @Value("${aiops.ai.ollama.maximum-context-tokens:16384}") int maximumOllamaContextTokens,
                       @Value("${aiops.ai.timeout-ms:300000}") long requestTimeoutMs,
                       @Value("${aiops.ai.helm-values-timeout-ms:600000}") long helmValuesRequestTimeoutMs) {
        this.configurations = configurations;
        this.crypto = crypto;
        this.mapper = mapper;
        this.maximumOllamaContextTokens = Math.max(4_096, maximumOllamaContextTokens);
        this.requestTimeout = boundedTimeout(requestTimeoutMs);
        this.helmValuesRequestTimeout = boundedTimeout(helmValuesRequestTimeoutMs);
    }

    /** ConfiguredAiClient의 complete 처리에 필요한 업무 로직을 수행한다. */
    Optional<Completion> complete(UUID tenantId, String purpose, String system, String user) {
        if (tenantId == null) return Optional.empty();
        TenantAiRoutingPolicy routing = configurations.findRouting(tenantId).stream()
                .filter(item -> purpose.equals(item.purpose())).findFirst().orElse(null);
        if (routing == null) return Optional.empty();
        try {
            return Optional.of(request(routing, routing.primaryProfileId(), routing.model(), system, user));
        } catch (RuntimeException primaryFailure) {
            if (routing.fallbackProfileId() == null) throw primaryFailure;
            return Optional.of(request(routing, routing.fallbackProfileId(), routing.fallbackModel(), system, user));
        }
    }

    /** ConfiguredAiClient의 request 처리에 필요한 업무 로직을 수행한다. */
    private Completion request(TenantAiRoutingPolicy routing, UUID profileId, String model, String system, String user) {
        AiProviderProfile profile = configurations.findProfile(profileId).orElseThrow();
        if (!profile.enabled()) throw new IllegalStateException("Configured AI provider is disabled");
        if (!"VALID".equals(profile.validationStatus())) throw new IllegalStateException("Configured AI provider is not validated");
        if (!"OLLAMA".equals(profile.providerType())
                && (!profile.externalDataTransfer() || !routing.externalTransferAllowed())) {
            throw new IllegalStateException("External AI transfer is not allowed by the active routing policy");
        }
        String safeModel = model == null || model.isBlank() ? profile.defaultModel() : model;
        // Values 계약을 중간에서 잘라 보내면 경로와 사용자 요구가 누락되므로 명시적으로 거부한다.
        if ("HELM_VALUES".equals(routing.purpose()) && user != null && user.length() > routing.maximumContextChars())
            throw new IllegalArgumentException("Helm Values reference exceeds configured context limit");
        String boundedUser = user == null ? "" : user.substring(0, Math.min(user.length(), routing.maximumContextChars()));
        long started = System.nanoTime();
        try {
            HttpRequest request = buildRequest(profile, safeModel, routing.purpose(), system, boundedUser,
                    routing.maximumOutputTokens());
            boolean structuredStream = "OLLAMA".equals(profile.providerType()) && "HELM_VALUES".equals(routing.purpose());
            HttpResponse<String> response;
            if (structuredStream) {
                var future = http.sendAsync(request, info -> info.statusCode() >= 200 && info.statusCode() < 300
                        ? new OllamaValuesBodySubscriber(mapper) : HttpResponse.BodySubscribers.replacing(""));
                try { response = future.get(timeoutFor(routing.purpose()).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS); }
                finally { if (!future.isDone()) future.cancel(true); }
            } else response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException("Configured AI provider returned HTTP " + response.statusCode());
            if (response.body() == null || response.body().length() > MAX_RESPONSE_CHARS)
                throw new IllegalStateException("Configured AI provider response exceeded the safe limit");
            String content = structuredStream ? response.body() : extract(profile.providerType(), mapper.readTree(response.body()));
            if (content == null || content.isBlank()) throw new IllegalStateException("Configured AI provider returned an empty response");
            long latencyMs = Math.max(1, (System.nanoTime() - started) / 1_000_000);
            log.info("AI completion succeeded purpose={} provider={} model={} latencyMs={} responseChars={}",
                    routing.purpose(), profile.providerType(), safeModel, latencyMs, content.length());
            return new Completion(content, safeModel, latencyMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Configured AI request was interrupted", exception);
        } catch (Exception exception) {
            if (exception instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("Configured AI request failed", exception);
        }
    }

    /** ConfiguredAiClient의 buildRequest 처리에 필요한 결과를 조합해 반환한다. */
    private HttpRequest buildRequest(AiProviderProfile profile, String model, String purpose, String system, String user,
                                     int maximumOutputTokens) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        String endpoint;
        if ("GOOGLE_GENAI".equals(profile.providerType())) {
            endpoint = profile.baseUrl() + "/v1beta/models/" + model + ":generateContent";
            ArrayNode contents = body.putArray("contents");
            contents.addObject().put("role", "user").putArray("parts")
                    .addObject().put("text", system + "\n\n" + user);
            body.putObject("generationConfig").put("maxOutputTokens", maximumOutputTokens);
        } else {
            endpoint = profile.baseUrl() + ("OLLAMA".equals(profile.providerType()) ? "/api/chat" : "/v1/chat/completions");
            body.put("model", model);
            body.put("stream", false);
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", system);
            messages.addObject().put("role", "user").put("content", user);
            if ("OLLAMA".equals(profile.providerType())) {
                if ("HELM_VALUES".equals(purpose)) {
                    body.put("stream", true);
                    body.set("format", HelmValuesResponseSchema.copy());
                    // 구조화된 변경안에 출력 예산을 사용하고 별도 추론 trace의 장시간 생성을 억제한다.
                    body.put("think", false);
                }
                OllamaRequestBudget.Budget budget = OllamaRequestBudget.calculate(system, user,
                        maximumOutputTokens, maximumOllamaContextTokens);
                log.info("Ollama request budget purpose={} model={} estimatedInputTokens={} contextTokens={} outputTokens={}",
                        purpose, model, budget.estimatedInputTokens(), budget.contextTokens(), budget.outputTokens());
                ObjectNode options = body.putObject("options");
                options.put("num_ctx", budget.contextTokens());
                options.put("num_predict", budget.outputTokens());
                options.put("temperature", "HELM_VALUES".equals(purpose) ? 0.1 : 0.2);
            } else body.put("max_tokens", maximumOutputTokens);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint)).timeout(timeoutFor(purpose))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        if (profile.credential() != null) {
            String key = crypto.decrypt(profile.credential());
            if ("GOOGLE_GENAI".equals(profile.providerType())) builder.header("x-goog-api-key", key);
            else builder.header("Authorization", "Bearer " + key);
        }
        return builder.build();
    }

    /** 목적별 AI 응답 제한시간을 30초~10분 범위로 제한한다. */
    private Duration boundedTimeout(long timeoutMs) {
        return Duration.ofMillis(Math.min(MAX_REQUEST_TIMEOUT_MS, Math.max(30_000L, timeoutMs)));
    }

    /** Values 요청은 로컬 모델의 긴 추론을 허용하고 나머지 요청은 기본 제한시간을 사용한다. */
    private Duration timeoutFor(String purpose) {
        return "HELM_VALUES".equals(purpose) ? helmValuesRequestTimeout : requestTimeout;
    }

    /** ConfiguredAiClient의 extract 처리에 필요한 업무 로직을 수행한다. */
    private String extract(String providerType, JsonNode response) {
        if ("OLLAMA".equals(providerType)) return response.path("message").path("content").asText();
        if ("GOOGLE_GENAI".equals(providerType))
            return response.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
        return response.path("choices").path(0).path("message").path("content").asText();
    }

    record Completion(String content, String model, long latencyMs) { }
}
