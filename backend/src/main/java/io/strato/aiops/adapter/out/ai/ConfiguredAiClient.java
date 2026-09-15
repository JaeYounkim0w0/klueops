package io.strato.aiops.adapter.out.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strato.aiops.application.port.out.AiProviderConfigurationRepositoryPort;
import io.strato.aiops.application.port.out.SecretCryptoPort;
import io.strato.aiops.domain.ai.AiProviderProfile;
import io.strato.aiops.domain.ai.TenantAiRoutingPolicy;
import org.springframework.stereotype.Component;

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
    private static final int MAX_RESPONSE_CHARS = 4_000_000;
    private final AiProviderConfigurationRepositoryPort configurations;
    private final SecretCryptoPort crypto;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    ConfiguredAiClient(AiProviderConfigurationRepositoryPort configurations, SecretCryptoPort crypto,
                       ObjectMapper mapper) {
        this.configurations = configurations;
        this.crypto = crypto;
        this.mapper = mapper;
    }

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

    private Completion request(TenantAiRoutingPolicy routing, UUID profileId, String model, String system, String user) {
        AiProviderProfile profile = configurations.findProfile(profileId).orElseThrow();
        if (!profile.enabled()) throw new IllegalStateException("Configured AI provider is disabled");
        if (!"VALID".equals(profile.validationStatus())) throw new IllegalStateException("Configured AI provider is not validated");
        if (!"OLLAMA".equals(profile.providerType())
                && (!profile.externalDataTransfer() || !routing.externalTransferAllowed())) {
            throw new IllegalStateException("External AI transfer is not allowed by the active routing policy");
        }
        String safeModel = model == null || model.isBlank() ? profile.defaultModel() : model;
        String boundedUser = user == null ? "" : user.substring(0, Math.min(user.length(), routing.maximumContextChars()));
        long started = System.nanoTime();
        try {
            HttpRequest request = buildRequest(profile, safeModel, system, boundedUser, routing.maximumOutputTokens());
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException("Configured AI provider returned HTTP " + response.statusCode());
            if (response.body() == null || response.body().length() > MAX_RESPONSE_CHARS)
                throw new IllegalStateException("Configured AI provider response exceeded the safe limit");
            String content = extract(profile.providerType(), mapper.readTree(response.body()));
            if (content == null || content.isBlank()) throw new IllegalStateException("Configured AI provider returned an empty response");
            return new Completion(content, safeModel, Math.max(1, (System.nanoTime() - started) / 1_000_000));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Configured AI request was interrupted", exception);
        } catch (Exception exception) {
            if (exception instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("Configured AI request failed", exception);
        }
    }

    private HttpRequest buildRequest(AiProviderProfile profile, String model, String system, String user,
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
            if (!"OLLAMA".equals(profile.providerType())) body.put("max_tokens", maximumOutputTokens);
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofMinutes(3))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        if (profile.credential() != null) {
            String key = crypto.decrypt(profile.credential());
            if ("GOOGLE_GENAI".equals(profile.providerType())) builder.header("x-goog-api-key", key);
            else builder.header("Authorization", "Bearer " + key);
        }
        return builder.build();
    }

    private String extract(String providerType, JsonNode response) {
        if ("OLLAMA".equals(providerType)) return response.path("message").path("content").asText();
        if ("GOOGLE_GENAI".equals(providerType))
            return response.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
        return response.path("choices").path(0).path("message").path("content").asText();
    }

    record Completion(String content, String model, long latencyMs) { }
}
