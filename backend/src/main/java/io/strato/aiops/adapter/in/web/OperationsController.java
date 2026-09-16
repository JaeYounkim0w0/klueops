package io.strato.aiops.adapter.in.web;

import io.strato.aiops.application.service.OperationsControlPlaneService;
import io.strato.aiops.application.service.IdentityAccessService;
import io.strato.aiops.application.service.ResolvedAccess;
import io.strato.aiops.adapter.in.web.security.ApiAuthorizationInterceptor;
import io.strato.aiops.domain.identity.Capability;
import io.strato.aiops.domain.audit.AuditLog;
import io.strato.aiops.domain.operations.OperationsModels.AiQualitySummary;
import io.strato.aiops.domain.operations.OperationsModels.AiCalibrationSummary;
import io.strato.aiops.domain.operations.OperationsModels.AnalysisFeedback;
import io.strato.aiops.domain.operations.OperationsModels.CleanupPreview;
import io.strato.aiops.domain.operations.OperationsModels.ClusterHealth;
import io.strato.aiops.domain.operations.OperationsModels.Incident;
import io.strato.aiops.domain.operations.OperationsModels.IncidentActivity;
import io.strato.aiops.domain.operations.OperationsModels.IncidentDetail;
import io.strato.aiops.domain.operations.OperationsModels.IncidentState;
import io.strato.aiops.domain.operations.OperationsModels.Notification;
import io.strato.aiops.domain.operations.OperationsModels.OperationSettings;
import io.strato.aiops.domain.operations.OperationsModels.OperationsOverview;
import io.strato.aiops.domain.operations.OperationsModels.OperationsScorecard;
import io.strato.aiops.domain.operations.OperationsModels.PolicyDefinition;
import io.strato.aiops.domain.operations.OperationsModels.PolicyEvaluation;
import io.strato.aiops.domain.operations.OperationsModels.PriorityItem;
import io.strato.aiops.domain.operations.OperationsModels.ResourceChange;
import io.strato.aiops.domain.operations.OperationsModels.RunbookTemplate;
import io.strato.aiops.domain.operations.OperationsModels.TriageQueue;
import io.strato.aiops.domain.operations.OperationsModels.WatchSignalGroup;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Tag(name = "AIOps Control Plane", description = "Operations dashboard, incidents, policies, runbooks, feedback and retention")
@RestController
@RequestMapping("/api")
public class OperationsController {

    private final OperationsControlPlaneService service;
    private final IdentityAccessService accessService;

    /** OperationsController 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public OperationsController(OperationsControlPlaneService service, IdentityAccessService accessService) {
        this.service = service;
        this.accessService = accessService;
    }

    /** OperationsController의 overview 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Get the integrated AIOps operations overview")
    @GetMapping("/operations/overview")
    public OperationsOverview overview() {
        return service.getOverview();
    }

    /** OperationsController의 reconcile 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Reconcile incidents, policies and resource baselines from current platform evidence")
    @PostMapping("/operations/reconcile")
    public OperationsOverview reconcile(HttpServletRequest request) {
        return service.reconcile(actor(request), requestId(request));
    }

    /** OperationsController의 priorityQueue 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "List operations priority items")
    @GetMapping("/operations/priority-queue")
    public List<PriorityItem> priorityQueue() {
        return service.getOverview().priorityQueue();
    }

    /** OperationsController의 clusterHealth 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "List cluster health posture")
    @GetMapping("/operations/cluster-health")
    public List<ClusterHealth> clusterHealth() {
        return service.getOverview().clusterHealth();
    }

    /** OperationsController의 aiQuality 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Get AI analysis quality summary")
    @GetMapping("/operations/ai-quality")
    public AiQualitySummary aiQuality() {
        return service.getAiQuality();
    }

    /** OperationsController의 aiCalibration 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Get AI ground-truth calibration by model and prompt version")
    @GetMapping("/operations/ai-calibration")
    public AiCalibrationSummary aiCalibration() {
        return service.getAiCalibration();
    }

    /** OperationsController의 scorecard 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Get operator scorecard without external metric integrations")
    @GetMapping("/operations/scorecard")
    public OperationsScorecard scorecard() {
        return service.getScorecard();
    }

    /** OperationsController의 triage 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "List deduplicated real-time triage signals")
    @GetMapping("/operations/triage")
    public TriageQueue triage(@RequestParam(required = false) UUID clusterId,
                              @RequestParam(required = false) String namespace,
                              @RequestParam(required = false) String state,
                              @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return service.getTriageQueue(clusterId, namespace, state, limit);
    }

    /** OperationsController의 updateTriageState 처리 대상의 상태를 갱신한다. */
    @Operation(summary = "Update a real-time triage signal lifecycle state")
    @PatchMapping("/operations/triage/{groupId}/state")
    public WatchSignalGroup updateTriageState(@PathVariable UUID groupId,
                                              @Valid @RequestBody TriageStateRequest body,
                                              HttpServletRequest request) {
        return service.updateSignalGroupState(groupId, body.state(), actor(request), requestId(request));
    }

    /** OperationsController의 incidents 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "List incidents")
    @GetMapping("/incidents")
    public List<Incident> incidents(@RequestParam(required = false) UUID clusterId,
                                    @RequestParam(required = false) String namespace,
                                    @RequestParam(required = false) String state,
                                    @RequestParam(required = false) String severity,
                                    @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit,
                                    HttpServletRequest request) {
        ResolvedAccess access = access(request);
        return service.listIncidents(clusterId, namespace, state, severity, limit).stream()
                .filter(item -> access == null || accessService.allows(access, Capability.ANALYSIS_READ, item.clusterId(), item.namespace()))
                .toList();
    }

    /** OperationsController의 incident 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Get incident details with evidence and timeline")
    @GetMapping("/incidents/{incidentId}")
    public IncidentDetail incident(@PathVariable UUID incidentId, HttpServletRequest request) {
        IncidentDetail detail = service.getIncident(incidentId);
        requireIncidentAccess(detail.incident(), Capability.ANALYSIS_READ, request);
        return detail;
    }

    /** OperationsController의 reconcileIncidents 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Reconcile incidents and all operations evidence")
    @PostMapping("/incidents/reconcile")
    public OperationsOverview reconcileIncidents(HttpServletRequest request) {
        return service.reconcile(actor(request), requestId(request));
    }

    /** OperationsController의 updateIncidentState 처리 대상의 상태를 갱신한다. */
    @Operation(summary = "Update incident lifecycle state")
    @PatchMapping("/incidents/{incidentId}/state")
    public Incident updateIncidentState(@PathVariable UUID incidentId,
                                        @Valid @RequestBody IncidentStateRequest body,
                                        HttpServletRequest request) {
        requireIncidentAccess(service.getIncident(incidentId).incident(), Capability.OPERATION_EXECUTE, request);
        return service.updateIncidentState(incidentId, body.state(), body.note(), actor(request), requestId(request));
    }

    /** OperationsController의 incidentTimeline 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "List incident timeline")
    @GetMapping("/incidents/{incidentId}/timeline")
    public List<IncidentActivity> incidentTimeline(@PathVariable UUID incidentId, HttpServletRequest request) {
        IncidentDetail detail = service.getIncident(incidentId);
        requireIncidentAccess(detail.incident(), Capability.ANALYSIS_READ, request);
        return detail.timeline();
    }

    /** OperationsController의 notifications 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "List internal notifications")
    @GetMapping("/notifications")
    public List<Notification> notifications(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return service.listNotifications(unreadOnly, limit);
    }

    /** OperationsController의 unreadCount 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Get unread notification count")
    @GetMapping("/notifications/unread-count")
    public UnreadCountResponse unreadCount() {
        return new UnreadCountResponse(service.unreadNotificationCount());
    }

    /** OperationsController의 readNotification 처리 결과를 조회해 반환한다. */
    @Operation(summary = "Mark one notification as read")
    @PatchMapping("/notifications/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readNotification(@PathVariable UUID notificationId, HttpServletRequest request) {
        service.markNotificationRead(notificationId, actor(request), requestId(request));
    }

    /** OperationsController의 readAllNotifications 처리 결과를 조회해 반환한다. */
    @Operation(summary = "Mark all notifications as read")
    @PostMapping("/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAllNotifications(HttpServletRequest request) {
        service.markAllNotificationsRead(actor(request), requestId(request));
    }

    /** OperationsController의 policies 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "List policy definitions")
    @GetMapping("/policies")
    public List<PolicyDefinition> policies() {
        return service.listPolicies();
    }

    /** OperationsController의 updatePolicy 처리 대상의 상태를 갱신한다. */
    @Operation(summary = "Update policy enablement and severity")
    @PutMapping("/policies/{policyId}")
    public PolicyDefinition updatePolicy(@PathVariable String policyId,
                                         @Valid @RequestBody PolicyUpdateRequest body,
                                         HttpServletRequest request) {
        return service.updatePolicy(policyId, body.enabled(), body.severity(), actor(request), requestId(request));
    }

    /** OperationsController의 evaluatePolicies 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Evaluate policies and reconcile resource baselines for a cluster")
    @PostMapping("/policies/evaluate")
    public List<PolicyEvaluation> evaluatePolicies(@RequestParam UUID clusterId, HttpServletRequest request) {
        return service.evaluatePolicies(clusterId, actor(request), requestId(request));
    }

    /** OperationsController의 policyEvaluations 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "List policy evaluations")
    @GetMapping("/policies/evaluations")
    public List<PolicyEvaluation> policyEvaluations(@RequestParam(required = false) UUID clusterId,
                                                    @RequestParam(required = false) String namespace,
                                                    @RequestParam(required = false) String result,
                                                    @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return service.listPolicyEvaluations(clusterId, namespace, result, limit);
    }

    /** OperationsController의 changes 처리 대상의 상태를 갱신한다. */
    @Operation(summary = "List safe resource change events")
    @GetMapping("/changes")
    public List<ResourceChange> changes(@RequestParam(required = false) UUID clusterId,
                                        @RequestParam(required = false) String namespace,
                                        @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return service.listChanges(clusterId, namespace, limit);
    }

    /** OperationsController의 change 처리 대상의 상태를 갱신한다. */
    @Operation(summary = "Get a safe resource change event")
    @GetMapping("/changes/{changeId}")
    public ResourceChange change(@PathVariable UUID changeId) {
        return service.getChange(changeId);
    }

    /** OperationsController의 runbooks 처리의 핵심 작업 흐름을 실행한다. */
    @Operation(summary = "List verified runbook templates")
    @GetMapping("/runbooks")
    public List<RunbookTemplate> runbooks(@RequestParam(required = false) String signal,
                                          @RequestParam(required = false) String category) {
        return service.listRunbooks(signal, category);
    }

    /** OperationsController의 runbook 처리의 핵심 작업 흐름을 실행한다. */
    @Operation(summary = "Get a verified runbook template")
    @GetMapping("/runbooks/{runbookId}")
    public RunbookTemplate runbook(@PathVariable String runbookId) {
        return service.getRunbook(runbookId);
    }

    /** OperationsController의 matchRunbooks 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Match verified runbooks to a Kubernetes signal")
    @PostMapping("/runbooks/match")
    public List<RunbookTemplate> matchRunbooks(@Valid @RequestBody RunbookMatchRequest body) {
        return service.matchRunbooks(body.signal(), body.category(), body.resourceKind());
    }

    /** OperationsController의 feedback 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Get operator feedback for an AI analysis")
    @GetMapping("/analysis/{analysisId}/feedback")
    public ResponseEntity<AnalysisFeedback> feedback(@PathVariable UUID analysisId) {
        AnalysisFeedback feedback = service.getFeedback(analysisId);
        return feedback == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(feedback);
    }

    /** OperationsController의 saveFeedback 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Operation(summary = "Save operator feedback for an AI analysis")
    @PutMapping("/analysis/{analysisId}/feedback")
    public AnalysisFeedback saveFeedback(@PathVariable UUID analysisId,
                                         @Valid @RequestBody AnalysisFeedbackRequest body,
                                         HttpServletRequest request) {
        return service.saveFeedback(analysisId, body.accuracy(), body.outcome(), body.dangerousSuggestion(),
                body.comment(), body.actualRootCause(), body.actualResolution(), body.validatedResourceKind(),
                body.validatedResourceName(), body.confidenceExpectation(), actor(request), requestId(request));
    }

    /** OperationsController의 settings 처리 대상의 상태를 갱신한다. */
    @Operation(summary = "Get operation and retention settings")
    @GetMapping("/settings/operations")
    public OperationSettings settings() {
        return service.getSettings();
    }

    /** OperationsController의 updateSettings 처리 대상의 상태를 갱신한다. */
    @Operation(summary = "Update operation and retention settings")
    @PutMapping("/settings/operations")
    public OperationSettings updateSettings(@Valid @RequestBody OperationSettingsRequest body,
                                             HttpServletRequest request) {
        return service.updateSettings(body.toDomain(actor(request)), actor(request), requestId(request));
    }

    /** OperationsController의 cleanupPreview 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Preview data cleanup using current retention settings")
    @PostMapping("/settings/operations/cleanup-preview")
    public CleanupPreview cleanupPreview() {
        return service.previewCleanup();
    }

    /** OperationsController의 cleanup 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Execute data cleanup using current retention settings")
    @PostMapping("/settings/operations/cleanup")
    public CleanupPreview cleanup(HttpServletRequest request) {
        return service.executeCleanup(actor(request), requestId(request));
    }

    /** OperationsController의 auditLogs 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "List platform audit logs")
    @GetMapping("/audit-logs")
    public List<AuditLogResponse> auditLogs(@RequestParam(required = false) String actor,
                                    @RequestParam(required = false) String action,
                                    @RequestParam(required = false) String targetType,
                                    @RequestParam(required = false) String targetId,
                                    @RequestParam(required = false) String requestId,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                    @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return service.listAuditLogs(actor, action, targetType, targetId, requestId, from, to, limit).stream()
                .map(AuditLogResponse::from)
                .toList();
    }

    /** OperationsController의 actor 처리에 필요한 업무 로직을 수행한다. */
    private String actor(HttpServletRequest request) {
        return request.getUserPrincipal() == null ? "anonymous" : request.getUserPrincipal().getName();
    }

    /** OperationsController의 access 처리에 필요한 업무 로직을 수행한다. */
    private ResolvedAccess access(HttpServletRequest request) {
        return (ResolvedAccess) request.getAttribute(ApiAuthorizationInterceptor.RESOLVED_ACCESS_ATTRIBUTE);
    }

    /** OperationsController의 requireIncidentAccess 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void requireIncidentAccess(Incident incident, Capability capability, HttpServletRequest request) {
        ResolvedAccess access = access(request);
        if (access != null && !accessService.allows(access, capability, incident.clusterId(), incident.namespace())) {
            throw new org.springframework.security.access.AccessDeniedException(capability.value() + " capability is not granted for this incident");
        }
    }

    /** OperationsController의 requestId 처리에 필요한 업무 로직을 수행한다. */
    private String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(RequestAttributes.REQUEST_ID));
    }

    public record IncidentStateRequest(@NotNull IncidentState state, @Size(max = 2000) String note) {
    }

    public record PolicyUpdateRequest(boolean enabled, @NotBlank String severity) {
    }

    public record UnreadCountResponse(long count) {
    }

    public record RunbookMatchRequest(@NotBlank String signal, String category, String resourceKind) {
    }

    public record AnalysisFeedbackRequest(@NotBlank String accuracy, @NotBlank String outcome,
                                          boolean dangerousSuggestion, @Size(max = 2000) String comment,
                                          @Size(max = 2000) String actualRootCause,
                                          @Size(max = 2000) String actualResolution,
                                          @Size(max = 100) String validatedResourceKind,
                                          @Size(max = 255) String validatedResourceName,
                                          String confidenceExpectation) {
    }

    public record TriageStateRequest(@NotBlank String state) {
    }

    public record AuditLogResponse(UUID id, String action, String targetType, String targetId, String actor,
                                   String requestId, Instant createdAt) {
        /** AuditLogResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static AuditLogResponse from(AuditLog auditLog) {
            return new AuditLogResponse(auditLog.id(), auditLog.action(), auditLog.targetType(), auditLog.targetId(),
                    auditLog.actor(), auditLog.requestId(), auditLog.createdAt());
        }
    }

    public record OperationSettingsRequest(
            @Min(1) @Max(365) int eventRetentionDays,
            @Min(7) @Max(730) int analysisRetentionDays,
            @Min(1) @Max(365) int jobRetentionDays,
            @Min(1) @Max(365) int notificationRetentionDays,
            @Min(7) @Max(730) int resolvedIncidentRetentionDays,
            @Min(1) @Max(365) int changeRetentionDays,
            @Min(30) @Max(2555) int auditRetentionDays,
            @Min(7) @Max(730) int commandRetentionDays,
            @Min(1) @Max(1440) int notificationSuppressMinutes,
            @Min(5) @Max(1440) int staleSyncMinutes,
            @Min(30) @Max(3600) int longRunningJobSeconds
    ) {
        /** OperationSettingsRequest의 toDomain 처리 데이터를 필요한 표현으로 변환한다. */
        OperationSettings toDomain(String actor) {
            return new OperationSettings(eventRetentionDays, analysisRetentionDays, jobRetentionDays,
                    notificationRetentionDays, resolvedIncidentRetentionDays, changeRetentionDays,
                    auditRetentionDays, commandRetentionDays,
                    notificationSuppressMinutes, staleSyncMinutes, longRunningJobSeconds, actor, Instant.now());
        }
    }
}
