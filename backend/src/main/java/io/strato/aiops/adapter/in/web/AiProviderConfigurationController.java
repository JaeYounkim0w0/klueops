package io.strato.aiops.adapter.in.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.adapter.in.web.security.CurrentAccessResolver;
import io.strato.aiops.application.service.AiProviderConfigurationService;
import io.strato.aiops.application.service.IdentityAccessService;
import io.strato.aiops.application.service.ResolvedAccess;
import io.strato.aiops.application.service.TenantFeatureGuard;
import io.strato.aiops.domain.ai.AiProviderProfile;
import io.strato.aiops.domain.ai.TenantAiRoutingPolicy;
import io.strato.aiops.domain.ai.LocalAiModel;
import io.strato.aiops.domain.identity.Capability;
import io.strato.aiops.domain.identity.FeatureKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v2/ai-configuration")
public class AiProviderConfigurationController {
    private final AiProviderConfigurationService service;
    private final CurrentAccessResolver resolver;
    private final IdentityAccessService accessService;
    private final ObjectMapper objectMapper;
    private final TenantFeatureGuard featureGuard;

    /** AiProviderConfigurationController 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AiProviderConfigurationController(AiProviderConfigurationService service, CurrentAccessResolver resolver,
                                             IdentityAccessService accessService, ObjectMapper objectMapper,
                                             TenantFeatureGuard featureGuard) {
        this.service = service;
        this.resolver = resolver;
        this.accessService = accessService;
        this.objectMapper = objectMapper;
        this.featureGuard = featureGuard;
    }

    /** AiProviderConfigurationController의 profiles 처리에 필요한 업무 로직을 수행한다. */
    @GetMapping("/providers")
    public List<ProviderResponse> profiles(@RequestParam UUID tenantId, Authentication authentication) {
        require(authentication, tenantId, Capability.AI_ROUTING_MANAGE);
        return service.profiles(tenantId).stream().map(item -> ProviderResponse.from(item, objectMapper)).toList();
    }

    /** AiProviderConfigurationController의 create 처리에 필요한 데이터를 생성하거나 저장한다. */
    @PostMapping("/providers")
    @ResponseStatus(HttpStatus.CREATED)
    public ProviderResponse create(@Valid @RequestBody ProviderRequest request, Authentication authentication) {
        ResolvedAccess actor = require(authentication, request.tenantId(), Capability.AI_PROVIDER_MANAGE);
        return ProviderResponse.from(service.create(null, request.name(), request.providerType(), request.baseUrl(),
                request.apiKey(), request.defaultModel(), request.allowedModels(), request.externalDataTransfer(),
                actor.user().id().toString()), objectMapper);
    }

    /** AiProviderConfigurationController의 update 처리 대상의 상태를 갱신한다. */
    @PutMapping("/providers/{profileId}")
    public ProviderResponse update(@PathVariable UUID profileId, @Valid @RequestBody UpdateProviderRequest request,
                                   Authentication authentication) {
        require(authentication, request.tenantId(), Capability.AI_PROVIDER_MANAGE);
        return ProviderResponse.from(service.update(request.tenantId(), profileId, request.name(), request.providerType(),
                request.baseUrl(), request.apiKey(), request.defaultModel(), request.allowedModels(), request.enabled(),
                request.externalDataTransfer()), objectMapper);
    }

    /** AiProviderConfigurationController의 validate 처리 입력과 현재 상태의 유효성을 검증한다. */
    @PostMapping("/providers/{profileId}/validate")
    public ValidationResponse validate(@PathVariable UUID profileId, @RequestParam UUID tenantId,
                                       Authentication authentication) {
        require(authentication, tenantId, Capability.AI_PROVIDER_MANAGE);
        var result = service.validate(profileId);
        return new ValidationResponse(result.valid(), result.message(), result.checkedAt().toString());
    }

    /** AiProviderConfigurationController의 localModels 처리에 필요한 업무 로직을 수행한다. */
    @GetMapping("/providers/{profileId}/models")
    public List<LocalModelResponse> localModels(@PathVariable UUID profileId, @RequestParam UUID tenantId,
                                                Authentication authentication) {
        require(authentication, tenantId, Capability.AI_MODEL_MANAGE);
        return service.localModels(tenantId, profileId).stream().map(LocalModelResponse::from).toList();
    }

    /** AiProviderConfigurationController의 refreshLocalModels 처리에 필요한 업무 로직을 수행한다. */
    @PostMapping("/providers/{profileId}/models/refresh")
    public List<LocalModelResponse> refreshLocalModels(@PathVariable UUID profileId, @RequestParam UUID tenantId,
                                                       Authentication authentication) {
        require(authentication, tenantId, Capability.AI_MODEL_MANAGE);
        return service.refreshLocalModels(tenantId, profileId).stream().map(LocalModelResponse::from).toList();
    }

    /** AiProviderConfigurationController의 pullLocalModel 처리에 필요한 업무 로직을 수행한다. */
    @PostMapping("/providers/{profileId}/models/pull")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ModelPullResponse pullLocalModel(@PathVariable UUID profileId, @Valid @RequestBody ModelPullRequest request,
                                            Authentication authentication) {
        require(authentication, request.tenantId(), Capability.AI_MODEL_MANAGE);
        var job = service.pullLocalModel(request.tenantId(), profileId, request.modelTag());
        return new ModelPullResponse(job.id(), "PENDING");
    }

    /** 사용 중 보호와 정확 확인 문구를 통과한 로컬 모델만 제거한다. */
    @DeleteMapping("/providers/{profileId}/models/{modelTag}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLocalModel(@PathVariable UUID profileId, @PathVariable String modelTag,
                                 @Valid @RequestBody ModelDeleteRequest request, Authentication authentication) {
        require(authentication, request.tenantId(), Capability.AI_MODEL_MANAGE);
        service.deleteLocalModel(request.tenantId(), profileId, modelTag, request.confirmationText());
    }

    /** 정식 소형 모델 코퍼스를 실제 모델에 실행한다. */
    @PostMapping("/providers/{profileId}/models/{modelTag}/evaluate")
    public ModelEvaluationResponse evaluateLocalModel(@PathVariable UUID profileId, @PathVariable String modelTag,
                                                       @RequestParam UUID tenantId, Authentication authentication) {
        require(authentication, tenantId, Capability.AI_MODEL_MANAGE);
        var result = service.evaluateLocalModel(tenantId, profileId, modelTag);
        return new ModelEvaluationResponse(result.score(), result.samples(), result.averageLatencyMs(), result.promotable());
    }

    /** 평가 gate를 통과한 모델을 Profile 기본 모델로 승격한다. */
    @PostMapping("/providers/{profileId}/models/{modelTag}/promote")
    public ProviderResponse promoteLocalModel(@PathVariable UUID profileId, @PathVariable String modelTag,
                                               @RequestParam UUID tenantId, Authentication authentication) {
        require(authentication, tenantId, Capability.AI_MODEL_MANAGE);
        return ProviderResponse.from(service.promoteLocalModel(tenantId, profileId, modelTag), objectMapper);
    }

    /** AiProviderConfigurationController의 routing 처리에 필요한 업무 로직을 수행한다. */
    @GetMapping("/routing")
    public List<RoutingResponse> routing(@RequestParam UUID tenantId, Authentication authentication) {
        require(authentication, tenantId, Capability.AI_ROUTING_MANAGE);
        return service.routing(tenantId).stream().map(RoutingResponse::from).toList();
    }

    /** AiProviderConfigurationController의 route 처리에 필요한 업무 로직을 수행한다. */
    @PutMapping("/routing/{purpose}")
    public RoutingResponse route(@PathVariable String purpose, @Valid @RequestBody RoutingRequest request,
                                 Authentication authentication) {
        ResolvedAccess actor = require(authentication, request.tenantId(), Capability.AI_ROUTING_MANAGE);
        return RoutingResponse.from(service.route(request.tenantId(), purpose, request.primaryProfileId(), request.model(),
                request.fallbackProfileId(), request.fallbackModel(), request.externalTransferAllowed(),
                request.maximumContextChars(), request.maximumOutputTokens(), actor.user().id().toString()));
    }

    /** AiProviderConfigurationController의 require 처리 입력과 현재 상태의 유효성을 검증한다. */
    private ResolvedAccess require(Authentication authentication, UUID tenantId, Capability capability) {
        featureGuard.requireEnabled(tenantId, FeatureKey.AI_PROVIDER_ROUTING);
        ResolvedAccess access = resolver.resolve(authentication);
        if (!accessService.allowsTenant(access, capability, tenantId)) throw new AccessDeniedException("Capability is not granted");
        return access;
    }

    public record ProviderRequest(@NotNull UUID tenantId, @NotBlank String name, @NotBlank String providerType,
                                  String baseUrl, String apiKey, @NotBlank String defaultModel,
                                  List<String> allowedModels, boolean externalDataTransfer) { }
    public record UpdateProviderRequest(@NotNull UUID tenantId, @NotBlank String name, @NotBlank String providerType,
                                        String baseUrl, String apiKey, @NotBlank String defaultModel,
                                        List<String> allowedModels, boolean enabled, boolean externalDataTransfer) { }
    public record RoutingRequest(@NotNull UUID tenantId, @NotNull UUID primaryProfileId, @NotBlank String model,
                                 UUID fallbackProfileId, String fallbackModel, boolean externalTransferAllowed,
                                 int maximumContextChars, int maximumOutputTokens) { }
    public record ValidationResponse(boolean valid, String message, String checkedAt) { }
    public record ModelPullRequest(@NotNull UUID tenantId, @NotBlank String modelTag) { }
    public record ModelDeleteRequest(@NotNull UUID tenantId, @NotBlank String confirmationText) { }
    public record ModelPullResponse(UUID jobId, String status) { }
    public record ModelEvaluationResponse(int score, int samples, long averageLatencyMs, boolean promotable) { }
    public record LocalModelResponse(UUID id, String modelTag, Double parameterBillions, String status,
                                     Long sizeBytes, String digest, Integer evaluationScore, Integer evaluationSamples,
                                     Long averageLatencyMs, boolean promoted, String updatedAt) {
        /** LocalModelResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static LocalModelResponse from(LocalAiModel value) {
            return new LocalModelResponse(value.id(), value.modelTag(), value.parameterBillions(), value.status(),
                    value.sizeBytes(), value.digest(), value.evaluationScore(), value.evaluationSamples(),
                    value.averageLatencyMs(), value.promoted(), value.updatedAt().toString());
        }
    }
    public record ProviderResponse(UUID id, String name, String providerType, String baseUrl, boolean credentialConfigured,
                                   String defaultModel, List<String> allowedModels, boolean enabled,
                                   boolean externalDataTransfer, String validationStatus, String lastValidatedAt) {
        /** ProviderResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static ProviderResponse from(AiProviderProfile value, ObjectMapper mapper) {
            try {
                List<String> models = mapper.readValue(value.allowedModelsJson(),
                        mapper.getTypeFactory().constructCollectionType(List.class, String.class));
                return new ProviderResponse(value.id(), value.name(), value.providerType(), value.baseUrl(),
                        value.credential() != null, value.defaultModel(), models, value.enabled(),
                        value.externalDataTransfer(), value.validationStatus(),
                        value.lastValidatedAt() == null ? null : value.lastValidatedAt().toString());
            } catch (JsonProcessingException exception) { throw new IllegalStateException("Allowed models are invalid", exception); }
        }
    }
    public record RoutingResponse(String purpose, UUID primaryProfileId, String model, UUID fallbackProfileId,
                                  String fallbackModel, boolean externalTransferAllowed, int maximumContextChars,
                                  int maximumOutputTokens, String updatedAt) {
        /** RoutingResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static RoutingResponse from(TenantAiRoutingPolicy value) {
            return new RoutingResponse(value.purpose(), value.primaryProfileId(), value.model(), value.fallbackProfileId(),
                    value.fallbackModel(), value.externalTransferAllowed(), value.maximumContextChars(),
                    value.maximumOutputTokens(), value.updatedAt().toString());
        }
    }
}
