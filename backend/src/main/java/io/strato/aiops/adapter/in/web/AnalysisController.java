package io.strato.aiops.adapter.in.web;

import io.strato.aiops.adapter.in.web.dto.AnalysisCommandExecutionResponse;
import io.strato.aiops.adapter.in.web.dto.AnalysisCommandPreviewResponse;
import io.strato.aiops.adapter.in.web.dto.AnalysisCommandRequest;
import io.strato.aiops.adapter.in.web.dto.AnalysisResponse;
import io.strato.aiops.adapter.in.web.dto.AnalysisWorkflowStateRequest;
import io.strato.aiops.adapter.in.web.dto.AnalysisWorkflowStateResponse;
import io.strato.aiops.adapter.in.web.dto.NamespaceDiagnosticsResponse;
import io.strato.aiops.adapter.in.web.dto.PodLogsResponse;
import io.strato.aiops.adapter.in.web.dto.StartAnalysisJobResponse;
import io.strato.aiops.application.port.in.AnalysisCommandExecuteCommand;
import io.strato.aiops.application.port.in.AnalysisCommandUseCase;
import io.strato.aiops.application.port.in.AnalysisUseCase;
import io.strato.aiops.application.port.in.AnalyzeApplicationCommand;
import io.strato.aiops.application.port.in.AnalyzeClusterCommand;
import io.strato.aiops.application.port.in.AnalyzeNamespaceCommand;
import io.strato.aiops.application.port.in.GetNamespaceDiagnosticsUseCase;
import io.strato.aiops.application.port.in.GetPodLogsUseCase;
import io.strato.aiops.application.port.in.UpdateAnalysisWorkflowStateCommand;
import io.strato.aiops.domain.analysis.SupportedLocale;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "AI Analysis", description = "AI-assisted Kubernetes analysis APIs")
@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisUseCase analysisUseCase;
    private final AnalysisCommandUseCase analysisCommandUseCase;
    private final GetNamespaceDiagnosticsUseCase getNamespaceDiagnosticsUseCase;
    private final GetPodLogsUseCase getPodLogsUseCase;

    /** AnalysisController 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AnalysisController(AnalysisUseCase analysisUseCase,
                              AnalysisCommandUseCase analysisCommandUseCase,
                              GetNamespaceDiagnosticsUseCase getNamespaceDiagnosticsUseCase,
                              GetPodLogsUseCase getPodLogsUseCase) {
        this.analysisUseCase = analysisUseCase;
        this.analysisCommandUseCase = analysisCommandUseCase;
        this.getNamespaceDiagnosticsUseCase = getNamespaceDiagnosticsUseCase;
        this.getPodLogsUseCase = getPodLogsUseCase;
    }

    /** AnalysisController의 analyzeApplication 처리의 핵심 작업 흐름을 실행한다. */
    @Operation(summary = "Analyze an application")
    @PostMapping("/applications/{applicationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisResponse analyzeApplication(@PathVariable UUID applicationId, HttpServletRequest request) {
        return AnalysisResponse.from(analysisUseCase.analyzeApplication(
                new AnalyzeApplicationCommand(applicationId, locale(request)), actor(request), requestId(request)));
    }

    /** AnalysisController의 startApplicationAnalysis 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Start application AI analysis asynchronously")
    @PostMapping("/applications/{applicationId}/jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StartAnalysisJobResponse startApplicationAnalysis(@PathVariable UUID applicationId, HttpServletRequest request) {
        return StartAnalysisJobResponse.from(analysisUseCase.startApplicationAnalysis(
                new AnalyzeApplicationCommand(applicationId, locale(request)), actor(request), requestId(request)));
    }

    /** AnalysisController의 analyzeNamespace 처리의 핵심 작업 흐름을 실행한다. */
    @Operation(summary = "Analyze a namespace")
    @PostMapping("/namespaces/{namespace}")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisResponse analyzeNamespace(@PathVariable String namespace, @RequestParam UUID clusterId, HttpServletRequest request) {
        return AnalysisResponse.from(analysisUseCase.analyzeNamespace(
                new AnalyzeNamespaceCommand(clusterId, namespace, locale(request)), actor(request), requestId(request)));
    }

    /** AnalysisController의 startNamespaceAnalysis 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Start namespace AI analysis asynchronously")
    @PostMapping("/namespaces/{namespace}/jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StartAnalysisJobResponse startNamespaceAnalysis(
            @PathVariable String namespace,
            @RequestParam UUID clusterId,
            HttpServletRequest request
    ) {
        return StartAnalysisJobResponse.from(analysisUseCase.startNamespaceAnalysis(
                new AnalyzeNamespaceCommand(clusterId, namespace, locale(request)), actor(request), requestId(request)));
    }

    /** AnalysisController의 analyzeCluster 처리의 핵심 작업 흐름을 실행한다. */
    @Operation(summary = "Analyze a cluster")
    @PostMapping("/clusters/{clusterId}")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisResponse analyzeCluster(@PathVariable UUID clusterId, HttpServletRequest request) {
        return AnalysisResponse.from(analysisUseCase.analyzeCluster(
                new AnalyzeClusterCommand(clusterId, locale(request)), actor(request), requestId(request)));
    }

    /** AnalysisController의 startClusterAnalysis 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Start cluster AI analysis asynchronously")
    @PostMapping("/clusters/{clusterId}/jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StartAnalysisJobResponse startClusterAnalysis(@PathVariable UUID clusterId, HttpServletRequest request) {
        return StartAnalysisJobResponse.from(analysisUseCase.startClusterAnalysis(
                new AnalyzeClusterCommand(clusterId, locale(request)), actor(request), requestId(request)));
    }

    /** AnalysisController의 retryAnalysis 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Retry an AI analysis session asynchronously")
    @PostMapping("/{analysisId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StartAnalysisJobResponse retryAnalysis(@PathVariable UUID analysisId, HttpServletRequest request) {
        return StartAnalysisJobResponse.from(analysisUseCase.retryAnalysis(analysisId, actor(request), requestId(request)));
    }

    /** AnalysisController의 getNamespaceDiagnostics 처리 결과를 조회해 반환한다. */
    @Operation(summary = "Preview namespace diagnostics context")
    @GetMapping("/namespaces/{namespace}/diagnostics")
    public NamespaceDiagnosticsResponse getNamespaceDiagnostics(@PathVariable String namespace, @RequestParam UUID clusterId) {
        return NamespaceDiagnosticsResponse.from(getNamespaceDiagnosticsUseCase.getNamespaceDiagnostics(clusterId, namespace));
    }

    /** AnalysisController의 getPodLogs 처리 결과를 조회해 반환한다. */
    @Operation(summary = "Get pod logs for analysis")
    @GetMapping("/namespaces/{namespace}/pods/{podName}/logs")
    public PodLogsResponse getPodLogs(
            @PathVariable String namespace,
            @PathVariable String podName,
            @RequestParam UUID clusterId,
            @RequestParam(required = false) String containerName,
            @RequestParam(defaultValue = "100") int tailLines
    ) {
        return PodLogsResponse.from(getPodLogsUseCase.getPodLogs(clusterId, namespace, podName, containerName, tailLines));
    }

    /** AnalysisController의 getResourceLogs 처리 결과를 조회해 반환한다. */
    @Operation(summary = "Get logs related to an analysis resource")
    @GetMapping("/namespaces/{namespace}/resources/{resourceType}/{resourceName}/logs")
    public PodLogsResponse getResourceLogs(
            @PathVariable String namespace,
            @PathVariable String resourceType,
            @PathVariable String resourceName,
            @RequestParam UUID clusterId,
            @RequestParam(required = false) String containerName,
            @RequestParam(defaultValue = "100") int tailLines
    ) {
        return PodLogsResponse.from(getPodLogsUseCase.getResourceLogs(
                clusterId, namespace, resourceType, resourceName, containerName, tailLines));
    }

    /** AnalysisController의 getAnalysis 처리 결과를 조회해 반환한다. */
    @Operation(summary = "Get an AI analysis session")
    @GetMapping("/{analysisId}")
    public AnalysisResponse getAnalysis(@PathVariable UUID analysisId) {
        return AnalysisResponse.from(analysisUseCase.getAnalysis(analysisId));
    }

    /** AnalysisController의 deleteAnalysis 처리 대상과 관련 상태를 안전하게 정리한다. */
    @Operation(summary = "Delete an AI analysis session")
    @DeleteMapping("/{analysisId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAnalysis(@PathVariable UUID analysisId, HttpServletRequest request) {
        analysisUseCase.deleteAnalysis(analysisId, actor(request), requestId(request));
    }

    /** AnalysisController의 previewCommand 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Preview a read-only analysis command")
    @PostMapping("/{analysisId}/commands/preview")
    public AnalysisCommandPreviewResponse previewCommand(@PathVariable UUID analysisId,
                                                         @RequestBody AnalysisCommandRequest body) {
        return AnalysisCommandPreviewResponse.from(analysisCommandUseCase.previewCommand(
                analysisId, new AnalysisCommandExecuteCommand(body.command(), body.confirmText())));
    }

    /** AnalysisController의 executeCommand 처리의 핵심 작업 흐름을 실행한다. */
    @Operation(summary = "Execute a safe read-only analysis command")
    @PostMapping("/{analysisId}/commands")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisCommandExecutionResponse executeCommand(@PathVariable UUID analysisId,
                                                           @RequestBody AnalysisCommandRequest body,
                                                           HttpServletRequest request) {
        return AnalysisCommandExecutionResponse.from(analysisCommandUseCase.executeCommand(
                analysisId, new AnalysisCommandExecuteCommand(body.command(), body.confirmText()), actor(request), requestId(request)));
    }

    /** AnalysisController의 listCommandExecutions 처리 결과를 조회해 반환한다. */
    @Operation(summary = "List analysis command executions")
    @GetMapping("/{analysisId}/commands")
    public List<AnalysisCommandExecutionResponse> listCommandExecutions(@PathVariable UUID analysisId) {
        return analysisCommandUseCase.listCommandExecutions(analysisId).stream()
                .map(AnalysisCommandExecutionResponse::from)
                .toList();
    }

    /** AnalysisController의 updateWorkflowState 처리 대상의 상태를 갱신한다. */
    @Operation(summary = "Update issue workflow state")
    @PostMapping("/{analysisId}/workflow/{issueGroupId}")
    public AnalysisWorkflowStateResponse updateWorkflowState(@PathVariable UUID analysisId,
                                                             @PathVariable String issueGroupId,
                                                             @RequestBody AnalysisWorkflowStateRequest body,
                                                             HttpServletRequest request) {
        return AnalysisWorkflowStateResponse.from(analysisCommandUseCase.updateWorkflowState(
                analysisId, issueGroupId, new UpdateAnalysisWorkflowStateCommand(body.status(), body.note()),
                actor(request), requestId(request)));
    }

    /** AnalysisController의 listWorkflowStates 처리 결과를 조회해 반환한다. */
    @Operation(summary = "List issue workflow states")
    @GetMapping("/{analysisId}/workflow")
    public List<AnalysisWorkflowStateResponse> listWorkflowStates(@PathVariable UUID analysisId) {
        return analysisCommandUseCase.listWorkflowStates(analysisId).stream()
                .map(AnalysisWorkflowStateResponse::from)
                .toList();
    }

    /** AnalysisController의 getAnalysisByJobId 처리 결과를 조회해 반환한다. */
    @Operation(summary = "Get an AI analysis session by async job ID")
    @GetMapping("/jobs/{jobId}/result")
    public AnalysisResponse getAnalysisByJobId(@PathVariable UUID jobId) {
        return AnalysisResponse.from(analysisUseCase.getAnalysisByJobId(jobId));
    }

    /** AnalysisController의 listHistory 처리 결과를 조회해 반환한다. */
    @Operation(summary = "List recent AI analysis sessions")
    @GetMapping("/history")
    public List<AnalysisResponse> listHistory(@RequestParam(required = false) UUID clusterId,
                                              @RequestParam(required = false) UUID applicationId,
                                              @RequestParam(required = false) String namespace) {
        return analysisUseCase.listHistory(clusterId, applicationId, namespace).stream().map(AnalysisResponse::from).toList();
    }

    /** AnalysisController의 actor 처리에 필요한 업무 로직을 수행한다. */
    private String actor(HttpServletRequest request) {
        return request.getUserPrincipal() == null ? "anonymous" : request.getUserPrincipal().getName();
    }

    /** AnalysisController의 requestId 처리에 필요한 업무 로직을 수행한다. */
    private String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(RequestAttributes.REQUEST_ID));
    }

    /** AnalysisController의 locale 처리에 필요한 업무 로직을 수행한다. */
    private SupportedLocale locale(HttpServletRequest request) {
        return SupportedLocale.fromAcceptLanguage(request.getHeader("Accept-Language"));
    }
}
