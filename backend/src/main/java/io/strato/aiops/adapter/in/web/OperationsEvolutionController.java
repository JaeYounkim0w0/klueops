package io.strato.aiops.adapter.in.web;

import io.strato.aiops.application.service.OperationsEventStream;
import io.strato.aiops.application.service.OperationsEvolutionService;
import io.strato.aiops.domain.operations.OperationsEvolutionModels.AiReleaseGate;
import io.strato.aiops.domain.operations.OperationsEvolutionModels.IncidentPostmortem;
import io.strato.aiops.domain.operations.OperationsEvolutionModels.RemediationObservation;
import io.strato.aiops.domain.operations.OperationsEvolutionModels.SignalNoisePolicy;
import io.strato.aiops.domain.operations.OperationsEvolutionModels.WatchContinuity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Tag(name = "Operations Reliability", description = "Watch continuity, live events, noise governance, remediation verification and AI release gates")
@RestController
@RequestMapping("/api")
public class OperationsEvolutionController {

    private final OperationsEvolutionService service;
    private final OperationsEventStream eventStream;

    /** OperationsEvolutionController 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public OperationsEvolutionController(OperationsEvolutionService service, OperationsEventStream eventStream) {
        this.service = service;
        this.eventStream = eventStream;
    }

    /** OperationsEvolutionController의 events 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Subscribe to real-time operations events; clients must re-fetch API state after reconnect")
    @GetMapping(value = "/operations/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        return eventStream.subscribe();
    }

    /** OperationsEvolutionController의 continuity 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "List persisted Watch continuity checkpoints")
    @GetMapping("/operations/watch/continuity")
    public List<WatchContinuity> continuity() {
        return service.watchContinuities();
    }

    /** OperationsEvolutionController의 noisePolicies 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "List signal noise and maintenance policies")
    @GetMapping("/operations/noise-policies")
    public List<SignalNoisePolicy> noisePolicies() {
        return service.noisePolicies();
    }

    /** OperationsEvolutionController의 createNoisePolicy 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Operation(summary = "Create a signal noise or maintenance policy")
    @PostMapping("/operations/noise-policies")
    @ResponseStatus(HttpStatus.CREATED)
    public SignalNoisePolicy createNoisePolicy(@Valid @RequestBody NoisePolicyRequest body,
                                               HttpServletRequest request) {
        return saveNoisePolicy(null, body, request);
    }

    /** OperationsEvolutionController의 updateNoisePolicy 처리 대상의 상태를 갱신한다. */
    @Operation(summary = "Update a signal noise or maintenance policy")
    @PutMapping("/operations/noise-policies/{policyId}")
    public SignalNoisePolicy updateNoisePolicy(@PathVariable UUID policyId,
                                               @Valid @RequestBody NoisePolicyRequest body,
                                               HttpServletRequest request) {
        return saveNoisePolicy(policyId, body, request);
    }

    /** OperationsEvolutionController의 deleteNoisePolicy 처리 대상과 관련 상태를 안전하게 정리한다. */
    @Operation(summary = "Delete a signal noise or maintenance policy")
    @DeleteMapping("/operations/noise-policies/{policyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNoisePolicy(@PathVariable UUID policyId, HttpServletRequest request) {
        service.deleteNoisePolicy(policyId, actor(request), requestId(request));
    }

    /** OperationsEvolutionController의 startObservation 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Start closed-loop Kubernetes state observation after a remediation")
    @PostMapping("/incidents/{incidentId}/remediation-observations")
    @ResponseStatus(HttpStatus.CREATED)
    public RemediationObservation startObservation(@PathVariable UUID incidentId,
                                                   @Valid @RequestBody ObservationRequest body,
                                                   HttpServletRequest request) {
        return service.startObservation(incidentId, body.analysisId(), body.commandExecutionId(),
                body.observationSeconds(), actor(request), requestId(request));
    }

    /** OperationsEvolutionController의 observations 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "List closed-loop remediation observations")
    @GetMapping("/incidents/{incidentId}/remediation-observations")
    public List<RemediationObservation> observations(@PathVariable UUID incidentId,
                                                     @RequestParam(required = false) String state,
                                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return service.observations(incidentId, state, limit);
    }

    /** OperationsEvolutionController의 evaluateObservation 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Evaluate a remediation observation against the latest Kubernetes snapshot")
    @PostMapping("/remediation-observations/{observationId}/evaluate")
    public RemediationObservation evaluateObservation(@PathVariable UUID observationId, HttpServletRequest request) {
        return service.evaluateObservation(observationId, actor(request), requestId(request));
    }

    /** OperationsEvolutionController의 cancelObservation 처리 조건의 충족 여부를 판단한다. */
    @Operation(summary = "Cancel a running remediation observation")
    @PostMapping("/remediation-observations/{observationId}/cancel")
    public RemediationObservation cancelObservation(@PathVariable UUID observationId, HttpServletRequest request) {
        return service.cancelObservation(observationId, actor(request), requestId(request));
    }

    /** OperationsEvolutionController의 evaluateReleaseGate 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Evaluate an AI model or prompt candidate against regression and ground truth thresholds")
    @PostMapping("/operations/ai-release-gates")
    @ResponseStatus(HttpStatus.CREATED)
    public AiReleaseGate evaluateReleaseGate(@Valid @RequestBody ReleaseGateRequest body,
                                             HttpServletRequest request) {
        return service.evaluateReleaseGate(body.candidateVersion(), body.baselineVersion(),
                body.minimumRegressionScore(), body.minimumGroundTruthSamples(),
                body.minimumVerifiedAccuracy(), actor(request), requestId(request));
    }

    /** OperationsEvolutionController의 releaseGates 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "List AI release gate decisions")
    @GetMapping("/operations/ai-release-gates")
    public List<AiReleaseGate> releaseGates(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return service.releaseGates(limit);
    }

    /** OperationsEvolutionController의 generatePostmortem 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Generate a factual incident postmortem draft")
    @PostMapping("/incidents/{incidentId}/postmortem")
    public IncidentPostmortem generatePostmortem(@PathVariable UUID incidentId, HttpServletRequest request) {
        return service.generatePostmortem(incidentId, actor(request), requestId(request));
    }

    /** OperationsEvolutionController의 postmortem 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Get the latest incident postmortem draft")
    @GetMapping("/incidents/{incidentId}/postmortem")
    public IncidentPostmortem postmortem(@PathVariable UUID incidentId) {
        return service.postmortem(incidentId);
    }

    /** OperationsEvolutionController의 saveNoisePolicy 처리에 필요한 데이터를 생성하거나 저장한다. */
    private SignalNoisePolicy saveNoisePolicy(UUID id, NoisePolicyRequest body, HttpServletRequest request) {
        return service.saveNoisePolicy(id, body.name(), body.clusterId(), body.namespacePattern(),
                body.severityFloor(), body.repeatThreshold(), body.maintenanceStart(), body.maintenanceEnd(),
                body.snoozeUntil(), body.enabled(), actor(request), requestId(request));
    }

    /** OperationsEvolutionController의 actor 처리에 필요한 업무 로직을 수행한다. */
    private String actor(HttpServletRequest request) {
        return request.getUserPrincipal() == null ? "anonymous" : request.getUserPrincipal().getName();
    }

    /** OperationsEvolutionController의 requestId 처리에 필요한 업무 로직을 수행한다. */
    private String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(RequestAttributes.REQUEST_ID));
    }

    public record NoisePolicyRequest(
            @NotBlank @Size(max = 255) String name,
            UUID clusterId,
            @Size(max = 255) String namespacePattern,
            @NotBlank String severityFloor,
            @Min(1) @Max(100) int repeatThreshold,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant maintenanceStart,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant maintenanceEnd,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant snoozeUntil,
            boolean enabled
    ) {
    }

    public record ObservationRequest(
            UUID analysisId,
            UUID commandExecutionId,
            @Min(30) @Max(3600) int observationSeconds
    ) {
    }

    public record ReleaseGateRequest(
            @NotBlank @Size(max = 255) String candidateVersion,
            @NotBlank @Size(max = 255) String baselineVersion,
            @Min(0) @Max(100) double minimumRegressionScore,
            @Min(0) @Max(1000) int minimumGroundTruthSamples,
            @Min(0) @Max(100) double minimumVerifiedAccuracy
    ) {
    }
}
