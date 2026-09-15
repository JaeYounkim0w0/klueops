package io.strato.aiops.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.strato.aiops.application.port.out.AiProviderConfigurationRepositoryPort;
import io.strato.aiops.application.port.out.SecretCryptoPort;
import io.strato.aiops.application.port.out.RemoteSourceValidationPort;
import io.strato.aiops.domain.ai.AiProviderProfile;
import io.strato.aiops.domain.ai.TenantAiRoutingPolicy;
import io.strato.aiops.domain.ai.LocalAiModel;
import io.strato.aiops.application.port.out.AsyncJobRepositoryPort;
import io.strato.aiops.domain.job.AsyncJob;
import io.strato.aiops.domain.job.AsyncJobType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
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
import java.util.regex.Pattern;
import java.net.http.HttpRequest.BodyPublishers;

@Service
public class AiProviderConfigurationService {
    private static final Set<String> PROVIDERS = Set.of("OLLAMA", "OPENAI", "GOOGLE_GENAI", "OPENAI_COMPATIBLE");
    private static final Set<String> PURPOSES = Set.of("ANALYSIS", "CHAT", "HELM_VALUES");
    private static final Pattern PARAMETER_SIZE = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*b");
    private final AiProviderConfigurationRepositoryPort repository;
    private final SecretCryptoPort crypto;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final AsyncJobRepositoryPort jobs;
    private final TaskExecutor modelExecutor;
    private final RemoteSourceValidationPort sourceValidator;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public AiProviderConfigurationService(AiProviderConfigurationRepositoryPort repository, SecretCryptoPort crypto,
                                          ObjectMapper objectMapper, Clock clock, AsyncJobRepositoryPort jobs,
                                          @Qualifier("aiModelExecutor") TaskExecutor modelExecutor,
                                          RemoteSourceValidationPort sourceValidator) {
        this.repository = repository;
        this.crypto = crypto;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.jobs = jobs;
        this.modelExecutor = modelExecutor;
        this.sourceValidator = sourceValidator;
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

    @Transactional
    public AiProviderProfile update(UUID tenantId, UUID profileId, String name, String providerType, String baseUrl,
                                    String apiKey, String defaultModel, List<String> allowedModels,
                                    boolean enabled, boolean externalDataTransfer) {
        AiProviderProfile current = visibleProfileIncludingDisabled(tenantId, profileId);
        requireProvider(providerType);
        String endpoint = normalizeBaseUrl(providerType, baseUrl);
        if (name == null || name.isBlank() || defaultModel == null || defaultModel.isBlank())
            throw new IllegalArgumentException("Provider name and default model are required");
        var credential = apiKey == null || apiKey.isBlank() ? current.credential() : crypto.encrypt(apiKey);
        List<String> models = allowedModels == null || allowedModels.isEmpty()
                ? List.of(defaultModel.trim()) : allowedModels.stream().map(String::trim).filter(value -> !value.isBlank()).toList();
        // Endpoint, Provider 종류 또는 모델 계약 변경 후에는 기존 연결 검증 결과를 재사용하지 않는다.
        boolean connectionChanged = !current.providerType().equals(providerType)
                || !current.baseUrl().equals(endpoint) || !current.defaultModel().equals(defaultModel.trim())
                || !current.allowedModelsJson().equals(json(models)) || credential != current.credential();
        return repository.saveProfile(new AiProviderProfile(current.id(), current.tenantId(), name.trim(), providerType,
                endpoint, credential, defaultModel.trim(), json(models), enabled, externalDataTransfer,
                connectionChanged ? "NOT_VALIDATED" : current.validationStatus(),
                connectionChanged ? null : current.lastValidatedAt(), current.createdBy(), current.createdAt(), clock.instant()));
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

    @Transactional(readOnly = true)
    public List<LocalAiModel> localModels(UUID tenantId, UUID profileId) {
        requireLocalProfile(tenantId, profileId);
        return repository.findLocalModels(profileId);
    }

    @Transactional
    public List<LocalAiModel> refreshLocalModels(UUID tenantId, UUID profileId) {
        AiProviderProfile profile = requireLocalProfile(tenantId, profileId);
        return synchronizeTags(profile);
    }

    @Transactional
    public AsyncJob pullLocalModel(UUID tenantId, UUID profileId, String modelTag) {
        AiProviderProfile profile = requireLocalProfile(tenantId, profileId);
        double parameters = requireAllowedLocalModel(profile, modelTag);
        LocalAiModel current = repository.findLocalModels(profileId).stream()
                .filter(item -> item.modelTag().equals(modelTag)).findFirst().orElse(null);
        UUID modelId = current == null ? UUID.randomUUID() : current.id();
        repository.saveLocalModel(new LocalAiModel(modelId, profileId, modelTag, parameters, "PULLING",
                current == null ? null : current.sizeBytes(), current == null ? null : current.digest(), clock.instant()));
        AsyncJob job = jobs.save(AsyncJob.pending(AsyncJobType.AI_MODEL_PULL));
        try {
            modelExecutor.execute(() -> executePull(profile, modelId, modelTag, parameters, job.id()));
        } catch (RuntimeException exception) {
            job.markSubmissionFailed(clock.instant(), "AI model worker queue is full");
            jobs.save(job);
            throw exception;
        }
        return job;
    }

    private void executePull(AiProviderProfile profile, UUID modelId, String modelTag, double parameters, UUID jobId) {
        AsyncJob job = jobs.findById(jobId).orElseThrow();
        job.markRunning(clock.instant());
        jobs.save(job);
        try {
            ObjectNode body = objectMapper.createObjectNode().put("name", modelTag).put("stream", false);
            HttpRequest request = HttpRequest.newBuilder(URI.create(profile.baseUrl() + "/api/pull"))
                    .timeout(Duration.ofMinutes(30)).header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException("Ollama model pull returned HTTP " + response.statusCode());
            List<LocalAiModel> refreshed = synchronizeTags(profile);
            if (refreshed.stream().noneMatch(item -> item.modelTag().equals(modelTag)))
                repository.saveLocalModel(new LocalAiModel(modelId, profile.id(), modelTag, parameters, "READY",
                        null, null, clock.instant()));
            job.markSucceeded(clock.instant());
            jobs.save(job);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failPull(modelId, profile.id(), modelTag, parameters, job, "AI model pull was interrupted");
        } catch (Exception exception) {
            failPull(modelId, profile.id(), modelTag, parameters, job, "AI model pull failed");
        }
    }

    private void failPull(UUID modelId, UUID profileId, String modelTag, double parameters, AsyncJob job, String message) {
        repository.saveLocalModel(new LocalAiModel(modelId, profileId, modelTag, parameters, "FAILED", null, null,
                clock.instant()));
        job.markFailed(clock.instant(), "AI_MODEL_PULL_FAILED", message);
        jobs.save(job);
    }

    private List<LocalAiModel> synchronizeTags(AiProviderProfile profile) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(profile.baseUrl() + "/api/tags"))
                    .timeout(Duration.ofSeconds(15)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException("Ollama model inventory returned HTTP " + response.statusCode());
            JsonNode models = objectMapper.readTree(response.body()).path("models");
            List<LocalAiModel> existing = repository.findLocalModels(profile.id());
            if (models.isArray()) models.forEach(item -> {
                String tag = item.path("name").asText(item.path("model").asText());
                if (tag.isBlank()) return;
                LocalAiModel previous = existing.stream().filter(value -> value.modelTag().equals(tag)).findFirst().orElse(null);
                Double parameters = parseParameters(item.path("details").path("parameter_size").asText(tag));
                repository.saveLocalModel(new LocalAiModel(previous == null ? UUID.randomUUID() : previous.id(),
                        profile.id(), tag, parameters, parameters != null && parameters <= 9 ? "READY" : "UNSUPPORTED",
                        item.path("size").isNumber() ? item.path("size").asLong() : null,
                        item.path("digest").asText(null), clock.instant()));
            });
            return repository.findLocalModels(profile.id());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ollama model inventory was interrupted", exception);
        } catch (Exception exception) {
            if (exception instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("Ollama model inventory failed", exception);
        }
    }

    private AiProviderProfile requireLocalProfile(UUID tenantId, UUID profileId) {
        AiProviderProfile profile = visibleProfile(tenantId, profileId);
        if (!"OLLAMA".equals(profile.providerType())) throw new IllegalArgumentException("Local models require an Ollama profile");
        return profile;
    }

    private double requireAllowedLocalModel(AiProviderProfile profile, String modelTag) {
        try {
            List<String> allowed = objectMapper.readValue(profile.allowedModelsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            if (!allowed.contains(modelTag)) throw new IllegalArgumentException("Model is not in the approved profile allowlist");
            Double parameters = parseParameters(modelTag);
            if (parameters == null || parameters > 9) throw new IllegalArgumentException("Only models up to 9B are allowed");
            return parameters;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Allowed model configuration is invalid", exception);
        }
    }

    private Double parseParameters(String value) {
        var matcher = PARAMETER_SIZE.matcher(value == null ? "" : value);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : null;
    }

    private AiProviderProfile visibleProfile(UUID tenantId, UUID profileId) {
        AiProviderProfile profile = visibleProfileIncludingDisabled(tenantId, profileId);
        if (!profile.enabled()) throw new IllegalStateException("AI provider profile is disabled");
        return profile;
    }

    private AiProviderProfile visibleProfileIncludingDisabled(UUID tenantId, UUID profileId) {
        AiProviderProfile profile = repository.findProfile(profileId).orElseThrow();
        if (profile.tenantId() != null && !profile.tenantId().equals(tenantId))
            throw new NoSuchElementException("AI provider profile not found");
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
        if (!"OLLAMA".equals(providerType)) sourceValidator.requirePublicHttps(value);
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
