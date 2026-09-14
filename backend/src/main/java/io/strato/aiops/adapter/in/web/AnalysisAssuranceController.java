package io.strato.aiops.adapter.in.web;

import io.strato.aiops.application.service.AnalysisRegressionService;
import io.strato.aiops.application.service.KubernetesWatchCoordinator;
import io.strato.aiops.domain.operations.OperationsModels.RegressionRun;
import io.strato.aiops.domain.operations.OperationsModels.WatchRuntimeStatus;
import io.strato.aiops.domain.operations.OperationsModels.WatchSignal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Analysis Assurance", description = "Kubernetes Watch health, real-time signals and AI analysis regression certification")
@RestController
@RequestMapping("/api")
public class AnalysisAssuranceController {

    private final KubernetesWatchCoordinator watchCoordinator;
    private final AnalysisRegressionService regressionService;

    public AnalysisAssuranceController(KubernetesWatchCoordinator watchCoordinator,
                                       AnalysisRegressionService regressionService) {
        this.watchCoordinator = watchCoordinator;
        this.regressionService = regressionService;
    }

    @Operation(summary = "List Kubernetes Watch runtime status for registered clusters")
    @GetMapping("/operations/watch/status")
    public List<WatchRuntimeStatus> watchStatus() {
        return watchCoordinator.statuses();
    }

    @Operation(summary = "List recent Kubernetes Watch signals")
    @GetMapping("/operations/watch/signals")
    public List<WatchSignal> watchSignals(@RequestParam(required = false) UUID clusterId,
                                          @RequestParam(required = false) String namespace,
                                          @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return watchCoordinator.signals(clusterId, namespace, limit);
    }

    @Operation(summary = "Reconnect Kubernetes Watch for one cluster")
    @PostMapping("/operations/watch/clusters/{clusterId}/restart")
    public WatchRuntimeStatus restartWatch(@PathVariable UUID clusterId) {
        return watchCoordinator.restart(clusterId);
    }

    @Operation(summary = "Pause Kubernetes Watch reconnects for one cluster")
    @PostMapping("/operations/watch/clusters/{clusterId}/pause")
    public WatchRuntimeStatus pauseWatch(@PathVariable UUID clusterId) {
        return watchCoordinator.pause(clusterId);
    }

    @Operation(summary = "Resume Kubernetes Watch for one cluster")
    @PostMapping("/operations/watch/clusters/{clusterId}/resume")
    public WatchRuntimeStatus resumeWatch(@PathVariable UUID clusterId) {
        return watchCoordinator.resume(clusterId);
    }

    @Operation(summary = "Execute deterministic AI analysis regression certification")
    @PostMapping("/analysis-regression/runs")
    public RegressionRun runRegression(HttpServletRequest request) {
        return regressionService.run(actor(request));
    }

    @Operation(summary = "List AI analysis regression certification runs")
    @GetMapping("/analysis-regression/runs")
    public List<RegressionRun> regressionRuns(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return regressionService.list(limit);
    }

    @Operation(summary = "Get one AI analysis regression certification run")
    @GetMapping("/analysis-regression/runs/{runId}")
    public RegressionRun regressionRun(@PathVariable UUID runId) {
        return regressionService.get(runId);
    }

    private String actor(HttpServletRequest request) {
        return request.getUserPrincipal() == null ? "anonymous" : request.getUserPrincipal().getName();
    }
}
