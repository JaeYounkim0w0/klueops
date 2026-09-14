package io.strato.aiops.adapter.in.web;

import io.strato.aiops.application.service.OperationsReadinessService;
import io.strato.aiops.domain.operations.OperationsReadinessModels.AnalysisBenchmark;
import io.strato.aiops.domain.operations.OperationsReadinessModels.FleetQueue;
import io.strato.aiops.domain.operations.OperationsReadinessModels.LiveValidationPolicy;
import io.strato.aiops.domain.operations.OperationsReadinessModels.LiveValidationPreview;
import io.strato.aiops.domain.operations.OperationsReadinessModels.LiveValidationRun;
import io.strato.aiops.domain.operations.OperationsReadinessModels.ReliabilityTrend;
import io.strato.aiops.domain.operations.OperationsReadinessModels.RemediationLearning;
import io.strato.aiops.domain.operations.OperationsReadinessModels.ShiftBriefing;
import io.strato.aiops.domain.operations.OperationsReadinessModels.ValidationLabRun;
import io.strato.aiops.domain.operations.OperationsReadinessModels.ValidationScenario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

@Tag(name = "Operations Readiness", description = "Fleet queue, shift briefing and safe AIOps validation lab")
@RestController
@RequestMapping("/api/operations")
public class OperationsReadinessController {

    private final OperationsReadinessService service;

    public OperationsReadinessController(OperationsReadinessService service) {
        this.service = service;
    }

    @Operation(summary = "Get the cross-cluster operations priority queue")
    @GetMapping("/fleet-queue")
    public FleetQueue fleetQueue() {
        return service.fleetQueue();
    }

    @Operation(summary = "Get an evidence-based operator shift briefing")
    @GetMapping("/shift-briefing")
    public ShiftBriefing shiftBriefing() {
        return service.shiftBriefing();
    }

    @Operation(summary = "List safe virtual AIOps validation scenarios")
    @GetMapping("/validation-lab/scenarios")
    public List<ValidationScenario> validationScenarios() {
        return service.scenarios();
    }

    @Operation(summary = "Execute the safe virtual AIOps validation suite")
    @PostMapping("/validation-lab/runs")
    public ValidationLabRun runValidation(HttpServletRequest request) {
        return service.runValidation(actor(request));
    }

    @Operation(summary = "Get guarded live validation safety policy")
    @GetMapping("/validation-lab/live/policy")
    public LiveValidationPolicy liveValidationPolicy() {
        return service.liveValidationPolicy();
    }

    @Operation(summary = "Preview live validation RBAC and safety checks without changing Kubernetes")
    @PostMapping("/validation-lab/live/preview")
    public LiveValidationPreview previewLiveValidation(@Valid @RequestBody LiveValidationPreviewRequest body) {
        return service.previewLiveValidation(body.clusterId(), body.scenarioId(), body.ttlSeconds());
    }

    @Operation(summary = "Run an isolated, TTL-bound live validation fixture")
    @PostMapping("/validation-lab/live/runs")
    @ResponseStatus(HttpStatus.CREATED)
    public LiveValidationRun runLiveValidation(@Valid @RequestBody LiveValidationRunRequest body,
                                               HttpServletRequest request) {
        return service.runLiveValidation(body.clusterId(), body.scenarioId(), body.ttlSeconds(),
                body.confirmation(), actor(request), requestId(request));
    }

    @Operation(summary = "Delete only the namespace owned by a live validation run")
    @DeleteMapping("/validation-lab/live/runs/{runId}")
    public LiveValidationRun cleanupLiveValidation(@PathVariable UUID runId, HttpServletRequest request) {
        return service.cleanupLiveValidation(runId, actor(request), requestId(request));
    }

    @Operation(summary = "Execute analysis benchmark and produce a release recommendation")
    @PostMapping("/validation-lab/benchmarks")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisBenchmark runBenchmark(HttpServletRequest request) {
        return service.runBenchmark(actor(request));
    }

    @Operation(summary = "Get the latest analysis benchmark")
    @GetMapping("/validation-lab/benchmarks/latest")
    public ResponseEntity<AnalysisBenchmark> latestBenchmark() {
        AnalysisBenchmark benchmark = service.latestBenchmark();
        return benchmark == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(benchmark);
    }

    @Operation(summary = "Get outcome-based remediation recommendations for an incident")
    @GetMapping("/incidents/{incidentId}/remediation-learning")
    public RemediationLearning remediationLearning(@PathVariable UUID incidentId) {
        return service.remediationLearning(incidentId);
    }

    @Operation(summary = "Get event-based fleet reliability trend without utilization claims")
    @GetMapping("/reliability-trend")
    public ReliabilityTrend reliabilityTrend(
            @RequestParam(required = false) UUID clusterId,
            @RequestParam(required = false) @Size(max = 255) String namespace,
            @RequestParam(defaultValue = "30") @Min(7) @Max(90) int days) {
        return service.reliabilityTrend(clusterId, namespace, days);
    }

    private String actor(HttpServletRequest request) {
        return request.getUserPrincipal() == null ? "anonymous" : request.getUserPrincipal().getName();
    }

    private String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(RequestAttributes.REQUEST_ID));
    }

    public record LiveValidationPreviewRequest(
            @NotNull UUID clusterId,
            @NotBlank @Size(max = 100) String scenarioId,
            @Min(120) @Max(3600) int ttlSeconds
    ) {
    }

    public record LiveValidationRunRequest(
            @NotNull UUID clusterId,
            @NotBlank @Size(max = 100) String scenarioId,
            @Min(120) @Max(3600) int ttlSeconds,
            @NotBlank @Size(max = 100) String confirmation
    ) {
    }
}
