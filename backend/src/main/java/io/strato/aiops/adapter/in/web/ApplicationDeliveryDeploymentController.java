package io.strato.aiops.adapter.in.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.strato.aiops.adapter.in.web.security.CurrentAccessResolver;
import io.strato.aiops.application.service.ApplicationDeliveryDeploymentService;
import io.strato.aiops.application.service.IdentityAccessService;
import io.strato.aiops.application.service.ResolvedAccess;
import io.strato.aiops.application.service.TenantFeatureGuard;
import io.strato.aiops.domain.applicationdelivery.ApplicationRelease;
import io.strato.aiops.domain.applicationdelivery.ApplicationExposureMode;
import io.strato.aiops.adapter.in.web.dto.ApplicationResponse;
import io.strato.aiops.domain.applicationdelivery.DeploymentPlan;
import io.strato.aiops.domain.applicationdelivery.ReleaseOperation;
import io.strato.aiops.domain.identity.Capability;
import io.strato.aiops.domain.identity.FeatureKey;
import io.strato.aiops.application.port.out.ApplicationRuntimeInspectionPort;
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
    private final TenantFeatureGuard featureGuard;

    /** ApplicationDeliveryDeploymentController 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public ApplicationDeliveryDeploymentController(ApplicationDeliveryDeploymentService service,
                                                   CurrentAccessResolver currentAccessResolver,
                                                   IdentityAccessService accessService, ObjectMapper objectMapper,
                                                   TenantFeatureGuard featureGuard) {
        this.service = service;
        this.currentAccessResolver = currentAccessResolver;
        this.accessService = accessService;
        this.objectMapper = objectMapper;
        this.featureGuard = featureGuard;
    }

    /** ApplicationDeliveryDeploymentController의 preview 처리에 필요한 업무 로직을 수행한다. */
    @PostMapping("/deployment-plans")
    @Operation(summary = "Render and inspect an immutable Helm deployment plan")
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentPlanResponse preview(@Valid @RequestBody PreviewRequest request, Authentication authentication) {
        ResolvedAccess actor = requireTarget(authentication, request.tenantId(), Capability.APPLICATION_DEPLOY,
                request.clusterId(), request.namespace());
        if (ApplicationExposureMode.fromNullable(request.exposureType()).requiresExposureCapability()
                && !accessService.allows(actor, Capability.APPLICATION_EXPOSURE,
                request.clusterId(), request.namespace())) {
            throw new AccessDeniedException("application:exposure capability is not granted for this tenant");
        }
        if (request.createNamespace()
                && !accessService.allows(actor, Capability.NAMESPACE_CREATE,
                request.clusterId(), request.namespace())) {
            throw new AccessDeniedException("namespace:create capability is not granted for this tenant");
        }
        return DeploymentPlanResponse.from(service.preview(request.tenantId(), request.applicationId(), request.clusterId(),
                request.chartVersionId(), request.valuesRevisionId(), request.namespace(), request.releaseName(), request.createNamespace(),
                request.exposureType(), request.hostname(), request.exposurePath(), request.backendServiceNamespace(), request.backendServiceName(),
                request.backendServicePort(), request.gatewayName(), request.gatewayNamespace(),
                actor.user().id().toString()), objectMapper);
    }

    /** ApplicationDeliveryDeploymentController의 targetOptions 처리에 필요한 업무 로직을 수행한다. */
    @PostMapping("/deployment-target-options")
    @Operation(summary = "Render Service ports and discover HTTP Gateways for a deployment target")
    public DeploymentTargetOptionsResponse targetOptions(@Valid @RequestBody TargetOptionsRequest request,
                                                         Authentication authentication) {
        ResolvedAccess actor = requireTarget(authentication, request.tenantId(), Capability.APPLICATION_DEPLOY,
                request.clusterId(), request.namespace());
        if (!accessService.allows(actor, Capability.APPLICATION_EXPOSURE, request.clusterId(), request.namespace())) {
            throw new AccessDeniedException("application:exposure capability is not granted for this tenant");
        }
        return DeploymentTargetOptionsResponse.from(service.targetOptions(request.tenantId(), request.clusterId(),
                request.chartVersionId(), request.valuesRevisionId(), request.namespace(), request.releaseName(),
                request.exposureType()));
    }

    /** ApplicationDeliveryDeploymentController의 deploy 처리에 필요한 업무 로직을 수행한다. */
    @PostMapping("/deployment-plans/{planId}/execute")
    @Operation(summary = "Execute an unexpired plan after exact text confirmation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DeploymentAcceptedResponse deploy(@PathVariable UUID planId, @Valid @RequestBody ExecuteRequest request,
                                             Authentication authentication) {
        DeploymentPlan plan = service.plan(request.tenantId(), planId);
        ResolvedAccess actor = requireTarget(authentication, request.tenantId(), Capability.APPLICATION_DEPLOY,
                plan.clusterId(), plan.namespace());
        var accepted = service.deploy(request.tenantId(), planId, request.confirmationText(),
                actor.user().id().toString());
        return new DeploymentAcceptedResponse(accepted.applicationId(), accepted.jobId(), accepted.operationId());
    }

    /** ApplicationDeliveryDeploymentController의 operations 처리에 필요한 업무 로직을 수행한다. */
    @GetMapping("/applications/{applicationId}/operations")
    public List<OperationResponse> operations(@PathVariable UUID applicationId, @RequestParam UUID tenantId,
                                               Authentication authentication) {
        requireApplicationTarget(authentication, tenantId, applicationId, Capability.APPLICATION_READ);
        return service.operations(tenantId, applicationId).stream().map(OperationResponse::from).toList();
    }

    /** ApplicationDeliveryDeploymentController의 applications 처리에 필요한 업무 로직을 수행한다. */
    @GetMapping("/applications")
    @Operation(summary = "List applications owned by one tenant")
    public List<ApplicationResponse> applications(@RequestParam UUID tenantId, Authentication authentication) {
        ResolvedAccess access = require(authentication, tenantId, Capability.APPLICATION_READ);
        return service.applications(tenantId).stream()
                .filter(item -> accessService.allows(access, Capability.APPLICATION_READ,
                        item.clusterId(), item.namespace()))
                .map(ApplicationResponse::from).toList();
    }

    /** ApplicationDeliveryDeploymentController의 runtime 처리의 핵심 작업 흐름을 실행한다. */
    @GetMapping("/applications/{applicationId}/runtime")
    public RuntimeOverviewResponse runtime(@PathVariable UUID applicationId, @RequestParam UUID tenantId,
                                           Authentication authentication) {
        requireApplicationTarget(authentication, tenantId, applicationId, Capability.APPLICATION_READ);
        return RuntimeOverviewResponse.from(service.runtime(tenantId, applicationId));
    }

    /** ApplicationDeliveryDeploymentController의 releases 처리에 필요한 업무 로직을 수행한다. */
    @GetMapping("/applications/{applicationId}/releases")
    public List<ReleaseResponse> releases(@PathVariable UUID applicationId, @RequestParam UUID tenantId,
                                          Authentication authentication) {
        requireApplicationTarget(authentication, tenantId, applicationId, Capability.APPLICATION_READ);
        return service.releases(tenantId, applicationId).stream().map(ReleaseResponse::from).toList();
    }

    /** ApplicationDeliveryDeploymentController의 rollbackConfirmation 처리에 필요한 업무 로직을 수행한다. */
    @GetMapping("/applications/{applicationId}/rollback-confirmation")
    public LifecycleConfirmationResponse rollbackConfirmation(@PathVariable UUID applicationId,
                                                              @RequestParam UUID tenantId,
                                                              @RequestParam int revision,
                                                              Authentication authentication) {
        requireApplicationTarget(authentication, tenantId, applicationId, Capability.APPLICATION_ROLLBACK);
        var confirmation = service.rollbackConfirmation(tenantId, applicationId, revision);
        return new LifecycleConfirmationResponse(confirmation.confirmationText(), confirmation.impactSummary());
    }

    /** ApplicationDeliveryDeploymentController의 rollback 처리에 필요한 업무 로직을 수행한다. */
    @PostMapping("/applications/{applicationId}/rollback")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DeploymentAcceptedResponse rollback(@PathVariable UUID applicationId,
                                               @Valid @RequestBody RollbackRequest request,
                                               Authentication authentication) {
        ResolvedAccess actor = requireApplicationTarget(authentication, request.tenantId(), applicationId,
                Capability.APPLICATION_ROLLBACK);
        var accepted = service.rollback(request.tenantId(), applicationId, request.revision(),
                request.confirmationText(), actor.user().id().toString());
        return new DeploymentAcceptedResponse(accepted.applicationId(), accepted.jobId(), accepted.operationId());
    }

    /** ApplicationDeliveryDeploymentController의 uninstallConfirmation 처리에 필요한 업무 로직을 수행한다. */
    @GetMapping("/applications/{applicationId}/uninstall-confirmation")
    public LifecycleConfirmationResponse uninstallConfirmation(@PathVariable UUID applicationId,
                                                               @RequestParam UUID tenantId,
                                                               Authentication authentication) {
        requireApplicationTarget(authentication, tenantId, applicationId, Capability.APPLICATION_DELETE);
        var confirmation = service.uninstallConfirmation(tenantId, applicationId);
        return new LifecycleConfirmationResponse(confirmation.confirmationText(), confirmation.impactSummary());
    }

    /** ApplicationDeliveryDeploymentController의 uninstall 처리에 필요한 업무 로직을 수행한다. */
    @PostMapping("/applications/{applicationId}/uninstall")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DeploymentAcceptedResponse uninstall(@PathVariable UUID applicationId,
                                                @Valid @RequestBody UninstallRequest request,
                                                Authentication authentication) {
        ResolvedAccess actor = requireApplicationTarget(authentication, request.tenantId(), applicationId,
                Capability.APPLICATION_DELETE);
        var accepted = service.uninstall(request.tenantId(), applicationId, request.confirmationText(),
                actor.user().id().toString(), request.preservePvcs(), request.preserveDns(), request.preserveTls());
        return new DeploymentAcceptedResponse(accepted.applicationId(), accepted.jobId(), accepted.operationId());
    }

    /** 실패한 uninstall/cleanup을 새 Job으로 재시도한다. */
    @PostMapping("/applications/{applicationId}/cleanup-retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DeploymentAcceptedResponse retryCleanup(@PathVariable UUID applicationId,
                                                    @Valid @RequestBody UninstallRequest request,
                                                    Authentication authentication) {
        ResolvedAccess actor = requireApplicationTarget(authentication, request.tenantId(), applicationId,
                Capability.APPLICATION_DELETE);
        var accepted = service.retryCleanup(request.tenantId(), applicationId, request.confirmationText(),
                actor.user().id().toString(), request.preservePvcs(), request.preserveDns(), request.preserveTls());
        return new DeploymentAcceptedResponse(accepted.applicationId(), accepted.jobId(), accepted.operationId());
    }

    /** ApplicationDeliveryDeploymentController의 require 처리 입력과 현재 상태의 유효성을 검증한다. */
    private ResolvedAccess require(Authentication authentication, UUID tenantId, Capability capability) {
        featureGuard.requireEnabled(tenantId, FeatureKey.APPLICATION_DELIVERY);
        ResolvedAccess access = currentAccessResolver.resolve(authentication);
        if (!accessService.allowsTenant(access, capability, tenantId)) {
            throw new AccessDeniedException(capability.value() + " capability is not granted for this tenant");
        }
        return access;
    }

    /** ApplicationDeliveryDeploymentController의 requireTarget 처리 입력과 현재 상태의 유효성을 검증한다. */
    private ResolvedAccess requireTarget(Authentication authentication, UUID tenantId, Capability capability,
                                         UUID clusterId, String namespace) {
        ResolvedAccess access = require(authentication, tenantId, capability);
        if (!accessService.allows(access, capability, clusterId, namespace)) {
            throw new AccessDeniedException(capability.value() + " capability is not granted for this target");
        }
        return access;
    }

    /** ApplicationDeliveryDeploymentController의 requireApplicationTarget 처리 입력과 현재 상태의 유효성을 검증한다. */
    private ResolvedAccess requireApplicationTarget(Authentication authentication, UUID tenantId, UUID applicationId,
                                                    Capability capability) {
        var application = service.application(tenantId, applicationId);
        return requireTarget(authentication, tenantId, capability, application.clusterId(), application.namespace());
    }

    public record PreviewRequest(@NotNull UUID tenantId, UUID applicationId, @NotNull UUID clusterId, @NotNull UUID chartVersionId,
                                 UUID valuesRevisionId, @NotBlank String namespace, @NotBlank String releaseName,
                                 boolean createNamespace,
                                 String exposureType, String hostname, String exposurePath,
                                 String backendServiceNamespace, String backendServiceName, Integer backendServicePort,
                                 String gatewayName, String gatewayNamespace) { }
    public record TargetOptionsRequest(@NotNull UUID tenantId, @NotNull UUID clusterId, @NotNull UUID chartVersionId,
                                       UUID valuesRevisionId, @NotBlank String namespace, @NotBlank String releaseName,
                                       String exposureType) { }
    public record ExecuteRequest(@NotNull UUID tenantId, @NotBlank String confirmationText) { }
    public record UninstallRequest(@NotNull UUID tenantId, @NotBlank String confirmationText,
                                   boolean preservePvcs, boolean preserveDns, boolean preserveTls) { }
    public record RollbackRequest(@NotNull UUID tenantId, int revision, @NotBlank String confirmationText) { }
    public record DeploymentAcceptedResponse(UUID applicationId, UUID jobId, UUID operationId) { }
    public record LifecycleConfirmationResponse(String confirmationText, String impactSummary) { }
    public record DeploymentTargetOptionsResponse(List<RenderedServiceResponse> services,
                                                   List<GatewayResponse> gateways,
                                                   String gatewayDiscoveryStatus,
                                                   String gatewayDiscoveryMessage) {
        /** DeploymentTargetOptionsResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static DeploymentTargetOptionsResponse from(ApplicationDeliveryDeploymentService.DeploymentTargetOptions value) {
            var discovery = value.gatewayDiscovery();
            return new DeploymentTargetOptionsResponse(
                    value.services().stream().map(RenderedServiceResponse::from).toList(),
                    discovery.gateways().stream().map(GatewayResponse::from).toList(),
                    discovery.status(), discovery.message());
        }
    }
    public record RenderedServiceResponse(String namespace, String name, String type, String portName, int port,
                                          String targetPort, Integer nodePort, String protocol, String appProtocol,
                                          String httpRouteCompatibility, String compatibilityMessage) {
        /** RenderedServiceResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static RenderedServiceResponse from(ApplicationDeliveryDeploymentService.RenderedServiceOption value) {
            return new RenderedServiceResponse(value.namespace(), value.name(), value.type(), value.portName(),
                    value.port(), value.targetPort(), value.nodePort(), value.protocol(), value.appProtocol(),
                    value.httpRouteCompatibility(), value.compatibilityMessage());
        }
    }
    public record GatewayResponse(String namespace, String name, String readiness, List<GatewayListenerResponse> listeners) {
        /** GatewayResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static GatewayResponse from(io.strato.aiops.application.port.out.ApplicationExposurePort.GatewayOption value) {
            return new GatewayResponse(value.namespace(), value.name(), value.readiness(),
                    value.listeners().stream().map(GatewayListenerResponse::from).toList());
        }
    }
    public record GatewayListenerResponse(String name, String protocol, Integer port, String hostname) {
        /** GatewayListenerResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static GatewayListenerResponse from(io.strato.aiops.application.port.out.ApplicationExposurePort.GatewayListener value) {
            return new GatewayListenerResponse(value.name(), value.protocol(), value.port(), value.hostname());
        }
    }
    public record RuntimeOverviewResponse(int readyPods, int totalPods, int restarts,
                                          List<ApplicationRuntimeInspectionPort.Workload> workloads,
                                          List<ApplicationRuntimeInspectionPort.Endpoint> endpoints) {
        /** RuntimeOverviewResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static RuntimeOverviewResponse from(ApplicationRuntimeInspectionPort.RuntimeOverview value) {
            return new RuntimeOverviewResponse(value.readyPods(), value.totalPods(), value.restarts(),
                    value.workloads(), value.endpoints());
        }
    }

    public record DeploymentPlanResponse(UUID id, UUID applicationId, UUID clusterId, UUID chartVersionId, UUID valuesRevisionId,
                                         String namespace, String releaseName, boolean createNamespace, String exposureType, String hostname,
                                         String exposurePath, String backendServiceNamespace, String backendServiceName, Integer backendServicePort,
                                         String gatewayName, String gatewayNamespace,
                                         String manifestSha256, List<String> warnings, String confirmationText,
                                         String renderedManifest, String expiresAt) {
        /** DeploymentPlanResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static DeploymentPlanResponse from(DeploymentPlan plan, ObjectMapper mapper) {
            try {
                List<String> warnings = mapper.readValue(plan.warningsJson(),
                        mapper.getTypeFactory().constructCollectionType(List.class, String.class));
                return new DeploymentPlanResponse(plan.id(), plan.applicationId(), plan.clusterId(), plan.chartVersionId(),
                        plan.valuesRevisionId(), plan.namespace(), plan.releaseName(), plan.createNamespace(), plan.exposureType(), plan.hostname(),
                        plan.exposurePath(), plan.backendServiceNamespace(), plan.backendServiceName(), plan.backendServicePort(), plan.gatewayName(),
                        plan.gatewayNamespace(),
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
        /** OperationResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static OperationResponse from(ReleaseOperation value) {
            return new OperationResponse(value.id(), value.asyncJobId(), value.operationType(), value.status(),
                    value.releaseRevision(), value.outputSummary(), value.errorMessage(), value.requestedBy(),
                    value.requestedAt().toString(), value.completedAt() == null ? null : value.completedAt().toString());
        }
    }

    public record ReleaseResponse(UUID id, int revision, UUID chartVersionId, UUID valuesRevisionId,
                                  String manifestSha256, String status, String createdBy, String createdAt) {
        /** ReleaseResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static ReleaseResponse from(ApplicationRelease value) {
            return new ReleaseResponse(value.id(), value.revision(), value.chartVersionId(), value.valuesRevisionId(),
                    value.manifestSha256(), value.status(), value.createdBy(), value.createdAt().toString());
        }
    }
}
