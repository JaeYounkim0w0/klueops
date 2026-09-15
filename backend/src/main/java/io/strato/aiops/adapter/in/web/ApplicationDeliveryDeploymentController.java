package io.strato.aiops.adapter.in.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.adapter.in.web.security.CurrentAccessResolver;
import io.strato.aiops.application.service.ApplicationDeliveryDeploymentService;
import io.strato.aiops.application.service.IdentityAccessService;
import io.strato.aiops.application.service.ResolvedAccess;
import io.strato.aiops.domain.applicationdelivery.ApplicationRelease;
import io.strato.aiops.adapter.in.web.dto.ApplicationResponse;
import io.strato.aiops.domain.applicationdelivery.DeploymentPlan;
import io.strato.aiops.domain.applicationdelivery.ReleaseOperation;
import io.strato.aiops.domain.identity.Capability;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v2/application-delivery")
@Tag(name = "Application Delivery Deployment", description = "Immutable preview and Helm release lifecycle")
public class ApplicationDeliveryDeploymentController {
    private final ApplicationDeliveryDeploymentService service;
    private final CurrentAccessResolver currentAccessResolver;
    private final IdentityAccessService accessService;
    private final ObjectMapper objectMapper;

    public ApplicationDeliveryDeploymentController(ApplicationDeliveryDeploymentService service,
                                                   CurrentAccessResolver currentAccessResolver,
                                                   IdentityAccessService accessService, ObjectMapper objectMapper) {
        this.service = service;
        this.currentAccessResolver = currentAccessResolver;
        this.accessService = accessService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/deployment-plans")
    @Operation(summary = "Render and inspect an immutable Helm deployment plan")
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentPlanResponse preview(@Valid @RequestBody PreviewRequest request, Authentication authentication) {
        ResolvedAccess actor = require(authentication, request.tenantId(), Capability.APPLICATION_DEPLOY);
        return DeploymentPlanResponse.from(service.preview(request.tenantId(), request.clusterId(),
                request.chartVersionId(), request.valuesRevisionId(), request.namespace(), request.releaseName(),
                request.exposureType(), request.hostname(), actor.user().id().toString()), objectMapper);
    }

    @PostMapping("/deployment-plans/{planId}/execute")
    @Operation(summary = "Execute an unexpired plan after exact text confirmation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DeploymentAcceptedResponse deploy(@PathVariable UUID planId, @Valid @RequestBody ExecuteRequest request,
                                             Authentication authentication) {
        ResolvedAccess actor = require(authentication, request.tenantId(), Capability.APPLICATION_DEPLOY);
        var accepted = service.deploy(request.tenantId(), planId, request.confirmationText(),
                actor.user().id().toString());
        return new DeploymentAcceptedResponse(accepted.applicationId(), accepted.jobId(), accepted.operationId());
    }

    @GetMapping("/applications/{applicationId}/operations")
    public List<OperationResponse> operations(@PathVariable UUID applicationId, @RequestParam UUID tenantId,
                                               Authentication authentication) {
        require(authentication, tenantId, Capability.APPLICATION_READ);
        return service.operations(tenantId, applicationId).stream().map(OperationResponse::from).toList();
    }

    @GetMapping("/applications")
    @Operation(summary = "List applications owned by one tenant")
    public List<ApplicationResponse> applications(@RequestParam UUID tenantId, Authentication authentication) {
        require(authentication, tenantId, Capability.APPLICATION_READ);
        return service.applications(tenantId).stream().map(ApplicationResponse::from).toList();
    }

    @GetMapping("/applications/{applicationId}/releases")
    public List<ReleaseResponse> releases(@PathVariable UUID applicationId, @RequestParam UUID tenantId,
                                          Authentication authentication) {
        require(authentication, tenantId, Capability.APPLICATION_READ);
        return service.releases(tenantId, applicationId).stream().map(ReleaseResponse::from).toList();
    }

    @GetMapping("/applications/{applicationId}/rollback-confirmation")
    public LifecycleConfirmationResponse rollbackConfirmation(@PathVariable UUID applicationId,
                                                              @RequestParam UUID tenantId,
                                                              @RequestParam int revision,
                                                              Authentication authentication) {
        require(authentication, tenantId, Capability.APPLICATION_ROLLBACK);
        var confirmation = service.rollbackConfirmation(tenantId, applicationId, revision);
        return new LifecycleConfirmationResponse(confirmation.confirmationText(), confirmation.impactSummary());
    }

    @PostMapping("/applications/{applicationId}/rollback")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DeploymentAcceptedResponse rollback(@PathVariable UUID applicationId,
                                               @Valid @RequestBody RollbackRequest request,
                                               Authentication authentication) {
        ResolvedAccess actor = require(authentication, request.tenantId(), Capability.APPLICATION_ROLLBACK);
        var accepted = service.rollback(request.tenantId(), applicationId, request.revision(),
                request.confirmationText(), actor.user().id().toString());
        return new DeploymentAcceptedResponse(accepted.applicationId(), accepted.jobId(), accepted.operationId());
    }

    @GetMapping("/applications/{applicationId}/uninstall-confirmation")
    public LifecycleConfirmationResponse uninstallConfirmation(@PathVariable UUID applicationId,
                                                               @RequestParam UUID tenantId,
                                                               Authentication authentication) {
        require(authentication, tenantId, Capability.APPLICATION_DELETE);
        var confirmation = service.uninstallConfirmation(tenantId, applicationId);
        return new LifecycleConfirmationResponse(confirmation.confirmationText(), confirmation.impactSummary());
    }

    @PostMapping("/applications/{applicationId}/uninstall")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DeploymentAcceptedResponse uninstall(@PathVariable UUID applicationId,
                                                @Valid @RequestBody ExecuteRequest request,
                                                Authentication authentication) {
        ResolvedAccess actor = require(authentication, request.tenantId(), Capability.APPLICATION_DELETE);
        var accepted = service.uninstall(request.tenantId(), applicationId, request.confirmationText(),
                actor.user().id().toString());
        return new DeploymentAcceptedResponse(accepted.applicationId(), accepted.jobId(), accepted.operationId());
    }

    private ResolvedAccess require(Authentication authentication, UUID tenantId, Capability capability) {
        ResolvedAccess access = currentAccessResolver.resolve(authentication);
        if (!accessService.allowsTenant(access, capability, tenantId)) {
            throw new AccessDeniedException(capability.value() + " capability is not granted for this tenant");
        }
        return access;
    }

    public record PreviewRequest(@NotNull UUID tenantId, @NotNull UUID clusterId, @NotNull UUID chartVersionId,
                                 UUID valuesRevisionId, @NotBlank String namespace, @NotBlank String releaseName,
                                 String exposureType, String hostname) { }
    public record ExecuteRequest(@NotNull UUID tenantId, @NotBlank String confirmationText) { }
    public record RollbackRequest(@NotNull UUID tenantId, int revision, @NotBlank String confirmationText) { }
    public record DeploymentAcceptedResponse(UUID applicationId, UUID jobId, UUID operationId) { }
    public record LifecycleConfirmationResponse(String confirmationText, String impactSummary) { }

    public record DeploymentPlanResponse(UUID id, UUID clusterId, UUID chartVersionId, UUID valuesRevisionId,
                                         String namespace, String releaseName, String exposureType, String hostname,
                                         String manifestSha256, List<String> warnings, String confirmationText,
                                         String renderedManifest, String expiresAt) {
        static DeploymentPlanResponse from(DeploymentPlan plan, ObjectMapper mapper) {
            try {
                List<String> warnings = mapper.readValue(plan.warningsJson(),
                        mapper.getTypeFactory().constructCollectionType(List.class, String.class));
                return new DeploymentPlanResponse(plan.id(), plan.clusterId(), plan.chartVersionId(),
                        plan.valuesRevisionId(), plan.namespace(), plan.releaseName(), plan.exposureType(), plan.hostname(),
                        plan.manifestSha256(), warnings, plan.confirmationText(), plan.renderedManifest(),
                        plan.expiresAt().toString());
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Deployment plan warnings are invalid", exception);
            }
        }
    }

    public record OperationResponse(UUID id, UUID jobId, String type, String status, Integer releaseRevision,
                                    String outputSummary, String errorMessage, String requestedBy,
                                    String requestedAt, String completedAt) {
        static OperationResponse from(ReleaseOperation value) {
            return new OperationResponse(value.id(), value.asyncJobId(), value.operationType(), value.status(),
                    value.releaseRevision(), value.outputSummary(), value.errorMessage(), value.requestedBy(),
                    value.requestedAt().toString(), value.completedAt() == null ? null : value.completedAt().toString());
        }
    }

    public record ReleaseResponse(UUID id, int revision, UUID chartVersionId, UUID valuesRevisionId,
                                  String manifestSha256, String status, String createdBy, String createdAt) {
        static ReleaseResponse from(ApplicationRelease value) {
            return new ReleaseResponse(value.id(), value.revision(), value.chartVersionId(), value.valuesRevisionId(),
                    value.manifestSha256(), value.status(), value.createdBy(), value.createdAt().toString());
        }
    }
}
