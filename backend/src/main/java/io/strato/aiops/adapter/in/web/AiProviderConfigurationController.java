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

    public AiProviderConfigurationController(AiProviderConfigurationService service, CurrentAccessResolver resolver,
                                             IdentityAccessService accessService, ObjectMapper objectMapper,
                                             TenantFeatureGuard featureGuard) {
        this.service = service;
        this.resolver = resolver;
        this.accessService = accessService;
        this.objectMapper = objectMapper;
        this.featureGuard = featureGuard;
    }

    @GetMapping("/providers")
    public List<ProviderResponse> profiles(@RequestParam UUID tenantId, Authentication authentication) {
        require(authentication, tenantId, Capability.AI_ROUTING_MANAGE);
        return service.profiles(tenantId).stream().map(item -> ProviderResponse.from(item, objectMapper)).toList();
    }

    @PostMapping("/providers")
    @ResponseStatus(HttpStatus.CREATED)
    public ProviderResponse create(@Valid @RequestBody ProviderRequest request, Authentication authentication) {
        ResolvedAccess actor = require(authentication, request.tenantId(), Capability.AI_PROVIDER_MANAGE);
        return ProviderResponse.from(service.create(null, request.name(), request.providerType(), request.baseUrl(),
                request.apiKey(), request.defaultModel(), request.allowedModels(), request.externalDataTransfer(),
                actor.user().id().toString()), objectMapper);
    }

    @PostMapping("/providers/{profileId}/validate")
    public ValidationResponse validate(@PathVariable UUID profileId, @RequestParam UUID tenantId,
                                       Authentication authentication) {
        require(authentication, tenantId, Capability.AI_PROVIDER_MANAGE);
        var result = service.validate(profileId);
        return new ValidationResponse(result.valid(), result.message(), result.checkedAt().toString());
    }

    @GetMapping("/providers/{profileId}/models")
    public List<LocalModelResponse> localModels(@PathVariable UUID profileId, @RequestParam UUID tenantId,
                                                Authentication authentication) {
        require(authentication, tenantId, Capability.AI_MODEL_MANAGE);
        return service.localModels(tenantId, profileId).stream().map(LocalModelResponse::from).toList();
    }

    @PostMapping("/providers/{profileId}/models/refresh")
    public List<LocalModelResponse> refreshLocalModels(@PathVariable UUID profileId, @RequestParam UUID tenantId,
                                                       Authentication authentication) {
        require(authentication, tenantId, Capability.AI_MODEL_MANAGE);
        return service.refreshLocalModels(tenantId, profileId).stream().map(LocalModelResponse::from).toList();
    }

    @PostMapping("/providers/{profileId}/models/pull")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ModelPullResponse pullLocalModel(@PathVariable UUID profileId, @Valid @RequestBody ModelPullRequest request,
                                            Authentication authentication) {
        require(authentication, request.tenantId(), Capability.AI_MODEL_MANAGE);
        var job = service.pullLocalModel(request.tenantId(), profileId, request.modelTag());
        return new ModelPullResponse(job.id(), "PENDING");
    }

    @GetMapping("/routing")
    public List<RoutingResponse> routing(@RequestParam UUID tenantId, Authentication authentication) {
        require(authentication, tenantId, Capability.AI_ROUTING_MANAGE);
        return service.routing(tenantId).stream().map(RoutingResponse::from).toList();
    }

    @PutMapping("/routing/{purpose}")
    public RoutingResponse route(@PathVariable String purpose, @Valid @RequestBody RoutingRequest request,
                                 Authentication authentication) {
        ResolvedAccess actor = require(authentication, request.tenantId(), Capability.AI_ROUTING_MANAGE);
        return RoutingResponse.from(service.route(request.tenantId(), purpose, request.primaryProfileId(), request.model(),
                request.fallbackProfileId(), request.fallbackModel(), request.externalTransferAllowed(),
                request.maximumContextChars(), request.maximumOutputTokens(), actor.user().id().toString()));
    }

    private ResolvedAccess require(Authentication authentication, UUID tenantId, Capability capability) {
        featureGuard.requireEnabled(tenantId, FeatureKey.AI_PROVIDER_ROUTING);
        ResolvedAccess access = resolver.resolve(authentication);
        if (!accessService.allowsTenant(access, capability, tenantId)) throw new AccessDeniedException("Capability is not granted");
        return access;
    }

    public record ProviderRequest(@NotNull UUID tenantId, @NotBlank String name, @NotBlank String providerType,
                                  String baseUrl, String apiKey, @NotBlank String defaultModel,
                                  List<String> allowedModels, boolean externalDataTransfer) { }
    public record RoutingRequest(@NotNull UUID tenantId, @NotNull UUID primaryProfileId, @NotBlank String model,
                                 UUID fallbackProfileId, String fallbackModel, boolean externalTransferAllowed,
                                 int maximumContextChars, int maximumOutputTokens) { }
    public record ValidationResponse(boolean valid, String message, String checkedAt) { }
    public record ModelPullRequest(@NotNull UUID tenantId, @NotBlank String modelTag) { }
    public record ModelPullResponse(UUID jobId, String status) { }
    public record LocalModelResponse(UUID id, String modelTag, Double parameterBillions, String status,
                                     Long sizeBytes, String digest, String updatedAt) {
        static LocalModelResponse from(LocalAiModel value) {
            return new LocalModelResponse(value.id(), value.modelTag(), value.parameterBillions(), value.status(),
                    value.sizeBytes(), value.digest(), value.updatedAt().toString());
        }
    }
    public record ProviderResponse(UUID id, String name, String providerType, String baseUrl, boolean credentialConfigured,
                                   String defaultModel, List<String> allowedModels, boolean enabled,
                                   boolean externalDataTransfer, String validationStatus, String lastValidatedAt) {
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
        static RoutingResponse from(TenantAiRoutingPolicy value) {
            return new RoutingResponse(value.purpose(), value.primaryProfileId(), value.model(), value.fallbackProfileId(),
                    value.fallbackModel(), value.externalTransferAllowed(), value.maximumContextChars(),
                    value.maximumOutputTokens(), value.updatedAt().toString());
        }
    }
}
