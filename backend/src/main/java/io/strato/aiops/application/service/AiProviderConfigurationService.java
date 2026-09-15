package io.strato.aiops.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.application.port.out.AiProviderConfigurationRepositoryPort;
import io.strato.aiops.application.port.out.SecretCryptoPort;
import io.strato.aiops.domain.ai.AiProviderProfile;
import io.strato.aiops.domain.ai.TenantAiRoutingPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
public class AiProviderConfigurationService {
    private static final Set<String> PROVIDERS = Set.of("OLLAMA", "OPENAI", "GOOGLE_GENAI", "OPENAI_COMPATIBLE");
    private static final Set<String> PURPOSES = Set.of("ANALYSIS", "CHAT", "HELM_VALUES");
    private final AiProviderConfigurationRepositoryPort repository;
    private final SecretCryptoPort crypto;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public AiProviderConfigurationService(AiProviderConfigurationRepositoryPort repository, SecretCryptoPort crypto,
                                          ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.crypto = crypto;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public AiProviderProfile create(UUID ownerTenantId, String name, String providerType, String baseUrl,
                                    String apiKey, String defaultModel, List<String> allowedModels,
                                    boolean externalDataTransfer, String actor) {
        requireProvider(providerType);
        String endpoint = normalizeBaseUrl(providerType, baseUrl);
        if (name == null || name.isBlank() || defaultModel == null || defaultModel.isBlank())
            throw new IllegalArgumentException("Provider name and default model are required");
        var now = clock.instant();
        return repository.saveProfile(new AiProviderProfile(UUID.randomUUID(), ownerTenantId, name.trim(), providerType,
                endpoint, apiKey == null || apiKey.isBlank() ? null : crypto.encrypt(apiKey), defaultModel.trim(),
                json(allowedModels == null || allowedModels.isEmpty() ? List.of(defaultModel.trim()) : allowedModels),
                true, externalDataTransfer, "NOT_VALIDATED", null, actor, now, now));
    }

    @Transactional(readOnly = true)
    public List<AiProviderProfile> profiles(UUID tenantId) { return repository.findVisibleProfiles(tenantId, 200); }

    @Transactional
    public ValidationResult validate(UUID profileId) {
        AiProviderProfile profile = repository.findProfile(profileId).orElseThrow();
        try {
            HttpRequest request = validationRequest(profile);
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            boolean valid = response.statusCode() >= 200 && response.statusCode() < 300;
            saveValidation(profile, valid ? "VALID" : "INVALID");
            return new ValidationResult(valid, valid ? "Provider connection succeeded"
                    : "Provider returned HTTP " + response.statusCode(), clock.instant());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            saveValidation(profile, "INVALID");
            return new ValidationResult(false, "Provider validation was interrupted", clock.instant());
        } catch (Exception exception) {
            saveValidation(profile, "INVALID");
            // Provider 오류 body나 credential은 반환하지 않고 분류된 메시지만 제공한다.
            return new ValidationResult(false, "Provider connection failed", clock.instant());
        }
    }

    @Transactional
    public TenantAiRoutingPolicy route(UUID tenantId, String purpose, UUID primaryProfileId, String model,
                                       UUID fallbackProfileId, String fallbackModel, boolean externalTransferAllowed,
                                       int maximumContextChars, int maximumOutputTokens, String actor) {
        if (!PURPOSES.contains(purpose)) throw new IllegalArgumentException("Unsupported AI purpose");
        AiProviderProfile primary = visibleProfile(tenantId, primaryProfileId);
        requireValidatedModel(primary, model);
        if (isExternal(primary) && !externalTransferAllowed)
            throw new IllegalArgumentException("External transfer must be explicitly allowed for this routing policy");
        if (fallbackProfileId != null) {
            AiProviderProfile fallback = visibleProfile(tenantId, fallbackProfileId);
            requireValidatedModel(fallback, fallbackModel);
            if (isExternal(fallback) && !externalTransferAllowed)
                throw new IllegalArgumentException("External transfer must cover the fallback provider");
        }
        return repository.saveRouting(new TenantAiRoutingPolicy(tenantId, purpose, primaryProfileId, model,
                fallbackProfileId, fallbackModel, externalTransferAllowed,
                Math.max(1000, Math.min(maximumContextChars, 200_000)),
                Math.max(256, Math.min(maximumOutputTokens, 32_000)), actor, clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<TenantAiRoutingPolicy> routing(UUID tenantId) { return repository.findRouting(tenantId); }

    private AiProviderProfile visibleProfile(UUID tenantId, UUID profileId) {
        AiProviderProfile profile = repository.findProfile(profileId).orElseThrow();
        if (profile.tenantId() != null && !profile.tenantId().equals(tenantId))
            throw new NoSuchElementException("AI provider profile not found");
        if (!profile.enabled()) throw new IllegalStateException("AI provider profile is disabled");
        return profile;
    }

    private HttpRequest validationRequest(AiProviderProfile profile) {
        String key = profile.credential() == null ? null : crypto.decrypt(profile.credential());
        String url = switch (profile.providerType()) {
            case "OLLAMA" -> profile.baseUrl() + "/api/tags";
            case "GOOGLE_GENAI" -> profile.baseUrl() + "/v1beta/models?key=" + java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8);
            default -> profile.baseUrl() + "/v1/models";
        };
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12)).GET();
        if (key != null && !"GOOGLE_GENAI".equals(profile.providerType())) builder.header("Authorization", "Bearer " + key);
        return builder.build();
    }

    private void saveValidation(AiProviderProfile profile, String status) {
        repository.saveProfile(new AiProviderProfile(profile.id(), profile.tenantId(), profile.name(), profile.providerType(),
                profile.baseUrl(), profile.credential(), profile.defaultModel(), profile.allowedModelsJson(), profile.enabled(),
                profile.externalDataTransfer(), status, clock.instant(), profile.createdBy(), profile.createdAt(), clock.instant()));
    }

    private String normalizeBaseUrl(String providerType, String baseUrl) {
        String value = baseUrl == null || baseUrl.isBlank() ? switch (providerType) {
            case "OPENAI" -> "https://api.openai.com";
            case "GOOGLE_GENAI" -> "https://generativelanguage.googleapis.com";
            default -> "";
        } : baseUrl.trim();
        URI uri = URI.create(value);
        if (uri.getHost() == null || !("https".equals(uri.getScheme())
                || ("OLLAMA".equals(providerType) && "http".equals(uri.getScheme()))))
            throw new IllegalArgumentException("Provider base URL is invalid");
        return value.replaceAll("/+$", "");
    }

    private void requireProvider(String providerType) {
        if (!PROVIDERS.contains(providerType)) throw new IllegalArgumentException("Unsupported AI provider type");
    }

    private boolean isExternal(AiProviderProfile profile) { return !"OLLAMA".equals(profile.providerType()); }
    private void requireValidatedModel(AiProviderProfile profile, String model) {
        if (!"VALID".equals(profile.validationStatus()))
            throw new IllegalArgumentException("AI provider must pass connection validation before routing");
        try {
            List<String> allowed = objectMapper.readValue(profile.allowedModelsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            if (model == null || !allowed.contains(model))
                throw new IllegalArgumentException("Selected model is not allowed by the provider profile");
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Allowed model configuration is invalid", exception);
        }
    }
    private String json(List<String> values) {
        try { return objectMapper.writeValueAsString(values); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Allowed models serialization failed", exception); }
    }

    public record ValidationResult(boolean valid, String message, java.time.Instant checkedAt) { }
}
