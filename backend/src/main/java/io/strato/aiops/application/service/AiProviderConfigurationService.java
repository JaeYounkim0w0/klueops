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

    /** AiProviderConfigurationService 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
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

    /** AiProviderConfigurationService의 create 처리에 필요한 데이터를 생성하거나 저장한다. */
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

    /** AiProviderConfigurationService의 update 처리 대상의 상태를 갱신한다. */
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

    /** AiProviderConfigurationService의 profiles 처리에 필요한 업무 로직을 수행한다. */
    @Transactional(readOnly = true)
    public List<AiProviderProfile> profiles(UUID tenantId) { return repository.findVisibleProfiles(tenantId, 200); }

    /** AiProviderConfigurationService의 validate 처리 입력과 현재 상태의 유효성을 검증한다. */
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

    /** AiProviderConfigurationService의 route 처리에 필요한 업무 로직을 수행한다. */
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

    /** AiProviderConfigurationService의 routing 처리에 필요한 업무 로직을 수행한다. */
    @Transactional(readOnly = true)
    public List<TenantAiRoutingPolicy> routing(UUID tenantId) { return repository.findRouting(tenantId); }

    /** AiProviderConfigurationService의 localModels 처리에 필요한 업무 로직을 수행한다. */
    @Transactional(readOnly = true)
    public List<LocalAiModel> localModels(UUID tenantId, UUID profileId) {
        requireLocalProfile(tenantId, profileId);
        return repository.findLocalModels(profileId);
    }

    /** AiProviderConfigurationService의 refreshLocalModels 처리에 필요한 업무 로직을 수행한다. */
    @Transactional
    public List<LocalAiModel> refreshLocalModels(UUID tenantId, UUID profileId) {
        AiProviderProfile profile = requireLocalProfile(tenantId, profileId);
        return synchronizeTags(profile);
    }

    /** AiProviderConfigurationService의 pullLocalModel 처리에 필요한 업무 로직을 수행한다. */
    @Transactional
    public AsyncJob pullLocalModel(UUID tenantId, UUID profileId, String modelTag) {
        AiProviderProfile profile = requireLocalProfile(tenantId, profileId);
        double parameters = requireAllowedLocalModel(profile, modelTag);
        LocalAiModel current = repository.findLocalModels(profileId).stream()
                .filter(item -> item.modelTag().equals(modelTag)).findFirst().orElse(null);
        UUID modelId = current == null ? UUID.randomUUID() : current.id();
        repository.saveLocalModel(LocalAiModel.inventory(modelId, profileId, modelTag, parameters, "PULLING",
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

    /** routing 또는 다운로드에서 사용 중이지 않은 Ollama 모델만 안전하게 삭제한다. */
    @Transactional
    public void deleteLocalModel(UUID tenantId, UUID profileId, String modelTag, String confirmationText) {
        AiProviderProfile profile = requireLocalProfile(tenantId, profileId);
        String expected = "DELETE MODEL " + modelTag;
        if (!expected.equals(confirmationText == null ? "" : confirmationText.trim()))
            throw new IllegalArgumentException("Model deletion confirmation text does not match");
        LocalAiModel model = repository.findLocalModels(profileId).stream()
                .filter(item -> item.modelTag().equals(modelTag)).findFirst().orElseThrow();
        if (repository.isModelRouted(profileId, modelTag))
            throw new IllegalStateException("Model is in use by a tenant AI routing policy");
        if ("PULLING".equals(model.status()) || "DELETING".equals(model.status()))
            throw new IllegalStateException("Model has an active lifecycle operation");
        repository.saveLocalModel(new LocalAiModel(model.id(), profileId, modelTag, model.parameterBillions(),
                "DELETING", model.sizeBytes(), model.digest(), model.evaluationScore(), model.evaluationSamples(),
                model.averageLatencyMs(), model.evaluatedAt(), model.promoted(), clock.instant()));
        try {
            ObjectNode body = objectMapper.createObjectNode().put("name", modelTag);
            HttpRequest request = HttpRequest.newBuilder(URI.create(profile.baseUrl() + "/api/delete"))
                    .timeout(Duration.ofMinutes(2)).header("Content-Type", "application/json")
                    .method("DELETE", BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException("Ollama model deletion returned HTTP " + response.statusCode());
            repository.deleteLocalModel(profileId, modelTag);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            restoreModel(model);
            throw new IllegalStateException("AI model deletion was interrupted", exception);
        } catch (Exception exception) {
            restoreModel(model);
            if (exception instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("AI model deletion failed", exception);
        }
    }

    /** 고정된 분석·대화·Helm Values 코퍼스를 실제 Ollama 모델에 실행해 비교 점수를 저장한다. */
    @Transactional
    public ModelEvaluationResult evaluateLocalModel(UUID tenantId, UUID profileId, String modelTag) {
        AiProviderProfile profile = requireLocalProfile(tenantId, profileId);
        LocalAiModel model = repository.findLocalModels(profileId).stream()
                .filter(item -> item.modelTag().equals(modelTag)).findFirst().orElseThrow();
        if (!"READY".equals(model.status()) && !"APPROVED".equals(model.status()))
            throw new IllegalStateException("Only a ready local model can be evaluated");
        List<EvaluationCase> corpus = List.of(
                new EvaluationCase("ANALYSIS", "Pod가 Pending이고 PVC가 Pending입니다. 원인 분류 한 단어: STORAGE", "STORAGE"),
                new EvaluationCase("ANALYSIS", "Kubernetes 403 forbidden의 원인 분류 한 단어: RBAC", "RBAC"),
                new EvaluationCase("CHAT", "위험한 변경 전 사용자 확인이 필요하면 한 단어로 CONFIRM", "CONFIRM"),
                new EvaluationCase("CHAT", "증거가 부족할 때 한 단어로 UNKNOWN", "UNKNOWN"),
                new EvaluationCase("HELM_VALUES", "replicaCount 2인 YAML 한 줄만 작성", "replicaCount: 2"),
                new EvaluationCase("HELM_VALUES", "service.type이 ClusterIP인 YAML 한 줄만 작성", "type: ClusterIP"));
        int passed = 0;
        long latency = 0;
        for (EvaluationCase item : corpus) {
            long started = System.nanoTime();
            String output = generate(profile, modelTag, item.prompt());
            latency += (System.nanoTime() - started) / 1_000_000;
            if (output.toLowerCase(java.util.Locale.ROOT).contains(item.expected().toLowerCase(java.util.Locale.ROOT))) passed++;
        }
        int score = Math.round((passed * 100f) / corpus.size());
        long averageLatency = latency / corpus.size();
        repository.saveLocalModel(new LocalAiModel(model.id(), profileId, modelTag, model.parameterBillions(),
                model.status(), model.sizeBytes(), model.digest(), score, corpus.size(), averageLatency,
                clock.instant(), model.promoted(), clock.instant()));
        return new ModelEvaluationResult(score, corpus.size(), averageLatency, score >= 80);
    }

    /** 합격한 9B 이하 모델만 Profile 기본 모델로 승격한다. */
    @Transactional
    public AiProviderProfile promoteLocalModel(UUID tenantId, UUID profileId, String modelTag) {
        AiProviderProfile profile = requireLocalProfile(tenantId, profileId);
        LocalAiModel candidate = repository.findLocalModels(profileId).stream()
                .filter(item -> item.modelTag().equals(modelTag)).findFirst().orElseThrow();
        if (candidate.evaluationScore() == null || candidate.evaluationSamples() == null
                || candidate.evaluationSamples() < 6 || candidate.evaluationScore() < 80)
            throw new IllegalStateException("Model must pass the formal evaluation corpus before promotion");
        if (candidate.parameterBillions() == null || candidate.parameterBillions() > 9)
            throw new IllegalStateException("Only models up to 9B can be promoted");
        repository.findLocalModels(profileId).forEach(item -> repository.saveLocalModel(new LocalAiModel(item.id(),
                item.providerProfileId(), item.modelTag(), item.parameterBillions(), item.status(), item.sizeBytes(),
                item.digest(), item.evaluationScore(), item.evaluationSamples(), item.averageLatencyMs(),
                item.evaluatedAt(), item.modelTag().equals(modelTag), clock.instant())));
        AiProviderProfile promoted = new AiProviderProfile(profile.id(), profile.tenantId(), profile.name(),
                profile.providerType(), profile.baseUrl(), profile.credential(), modelTag, profile.allowedModelsJson(),
                profile.enabled(), profile.externalDataTransfer(), profile.validationStatus(), profile.lastValidatedAt(),
                profile.createdBy(), profile.createdAt(), clock.instant());
        return repository.saveProfile(promoted);
    }

    /** Ollama generate API를 짧고 결정적인 평가 설정으로 호출한다. */
    private String generate(AiProviderProfile profile, String modelTag, String prompt) {
        try {
            ObjectNode body = objectMapper.createObjectNode().put("model", modelTag).put("prompt", prompt).put("stream", false);
            body.putObject("options").put("temperature", 0).put("num_predict", 48);
            HttpRequest request = HttpRequest.newBuilder(URI.create(profile.baseUrl() + "/api/generate"))
                    .timeout(Duration.ofSeconds(45)).header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException("Model evaluation returned HTTP " + response.statusCode());
            return objectMapper.readTree(response.body()).path("response").asText();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Model evaluation was interrupted", exception);
        } catch (Exception exception) {
            if (exception instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Model evaluation failed", exception);
        }
    }

    /** 삭제 실패 시 inventory 상태를 원래 값으로 복원한다. */
    private void restoreModel(LocalAiModel model) {
        repository.saveLocalModel(new LocalAiModel(model.id(), model.providerProfileId(), model.modelTag(),
                model.parameterBillions(), model.status(), model.sizeBytes(), model.digest(), model.evaluationScore(),
                model.evaluationSamples(), model.averageLatencyMs(), model.evaluatedAt(), model.promoted(), clock.instant()));
    }

    /** AiProviderConfigurationService의 executePull 처리의 핵심 작업 흐름을 실행한다. */
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
                repository.saveLocalModel(LocalAiModel.inventory(modelId, profile.id(), modelTag, parameters, "READY",
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

    /** AiProviderConfigurationService의 failPull 처리에 필요한 업무 로직을 수행한다. */
    private void failPull(UUID modelId, UUID profileId, String modelTag, double parameters, AsyncJob job, String message) {
        repository.saveLocalModel(LocalAiModel.inventory(modelId, profileId, modelTag, parameters, "FAILED", null, null,
                clock.instant()));
        job.markFailed(clock.instant(), "AI_MODEL_PULL_FAILED", message);
        jobs.save(job);
    }

    /** AiProviderConfigurationService의 synchronizeTags 처리의 핵심 작업 흐름을 실행한다. */
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
                        item.path("digest").asText(null), previous == null ? null : previous.evaluationScore(),
                        previous == null ? null : previous.evaluationSamples(), previous == null ? null : previous.averageLatencyMs(),
                        previous == null ? null : previous.evaluatedAt(), previous != null && previous.promoted(), clock.instant()));
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

    /** AiProviderConfigurationService의 requireLocalProfile 처리 입력과 현재 상태의 유효성을 검증한다. */
    private AiProviderProfile requireLocalProfile(UUID tenantId, UUID profileId) {
        AiProviderProfile profile = visibleProfile(tenantId, profileId);
        if (!"OLLAMA".equals(profile.providerType())) throw new IllegalArgumentException("Local models require an Ollama profile");
        return profile;
    }

    /** AiProviderConfigurationService의 requireAllowedLocalModel 처리 입력과 현재 상태의 유효성을 검증한다. */
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

    /** AiProviderConfigurationService의 parseParameters 처리 데이터를 필요한 표현으로 변환한다. */
    private Double parseParameters(String value) {
        var matcher = PARAMETER_SIZE.matcher(value == null ? "" : value);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : null;
    }

    /** AiProviderConfigurationService의 visibleProfile 처리에 필요한 업무 로직을 수행한다. */
    private AiProviderProfile visibleProfile(UUID tenantId, UUID profileId) {
        AiProviderProfile profile = visibleProfileIncludingDisabled(tenantId, profileId);
        if (!profile.enabled()) throw new IllegalStateException("AI provider profile is disabled");
        return profile;
    }

    /** AiProviderConfigurationService의 visibleProfileIncludingDisabled 처리에 필요한 업무 로직을 수행한다. */
    private AiProviderProfile visibleProfileIncludingDisabled(UUID tenantId, UUID profileId) {
        AiProviderProfile profile = repository.findProfile(profileId).orElseThrow();
        if (profile.tenantId() != null && !profile.tenantId().equals(tenantId))
            throw new NoSuchElementException("AI provider profile not found");
        return profile;
    }

    /** AiProviderConfigurationService의 validationRequest 처리에 필요한 업무 로직을 수행한다. */
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

    /** AiProviderConfigurationService의 saveValidation 처리에 필요한 데이터를 생성하거나 저장한다. */
    private void saveValidation(AiProviderProfile profile, String status) {
        repository.saveProfile(new AiProviderProfile(profile.id(), profile.tenantId(), profile.name(), profile.providerType(),
                profile.baseUrl(), profile.credential(), profile.defaultModel(), profile.allowedModelsJson(), profile.enabled(),
                profile.externalDataTransfer(), status, clock.instant(), profile.createdBy(), profile.createdAt(), clock.instant()));
    }

    /** AiProviderConfigurationService의 normalizeBaseUrl 처리 데이터를 필요한 표현으로 변환한다. */
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

    /** AiProviderConfigurationService의 requireProvider 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void requireProvider(String providerType) {
        if (!PROVIDERS.contains(providerType)) throw new IllegalArgumentException("Unsupported AI provider type");
    }

    /** AiProviderConfigurationService의 isExternal 처리 조건의 충족 여부를 판단한다. */
    private boolean isExternal(AiProviderProfile profile) { return !"OLLAMA".equals(profile.providerType()); }
    /** AiProviderConfigurationService의 requireValidatedModel 처리 입력과 현재 상태의 유효성을 검증한다. */
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
    /** AiProviderConfigurationService의 json 처리에 필요한 업무 로직을 수행한다. */
    private String json(List<String> values) {
        try { return objectMapper.writeValueAsString(values); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Allowed models serialization failed", exception); }
    }

    public record ValidationResult(boolean valid, String message, java.time.Instant checkedAt) { }
    public record ModelEvaluationResult(int score, int samples, long averageLatencyMs, boolean promotable) { }
    private record EvaluationCase(String purpose, String prompt, String expected) { }
}
