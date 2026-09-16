package io.strato.aiops.adapter.in.web;

import io.strato.aiops.adapter.in.web.security.ApiAuthorizationInterceptor;
import io.strato.aiops.application.service.OperatorWorkspaceService;
import io.strato.aiops.application.service.OperatorWorkspaceService.RunbookInput;
import io.strato.aiops.application.service.ResolvedAccess;
import io.strato.aiops.domain.operations.OperationsModels.Incident;
import io.strato.aiops.domain.operator.OperatorWorkspaceModels.IncidentCollaboration;
import io.strato.aiops.domain.operator.OperatorWorkspaceModels.ManagedRunbook;
import io.strato.aiops.domain.operator.OperatorWorkspaceModels.ResourceContext;
import io.strato.aiops.domain.operator.OperatorWorkspaceModels.RunbookVersion;
import io.strato.aiops.domain.operator.OperatorWorkspaceModels.SearchResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import java.util.Set;
import java.util.UUID;

@Tag(name = "Operator Workspace", description = "Cross-resource search, incident collaboration and managed runbooks")
@RestController
@RequestMapping("/api")
public class OperatorWorkspaceController {

    private final OperatorWorkspaceService service;

    /** OperatorWorkspaceController 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public OperatorWorkspaceController(OperatorWorkspaceService service) {
        this.service = service;
    }

    /** OperatorWorkspaceController의 search 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Search accessible clusters, resources, incidents, analyses and runbooks")
    @GetMapping("/search")
    public List<SearchResult> search(@RequestParam @NotBlank String q,
                                     @RequestParam(required = false) UUID clusterId,
                                     @RequestParam(required = false) String namespace,
                                     @RequestParam(required = false) Set<String> types,
                                     @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit,
                                     HttpServletRequest request) {
        return service.search(q, clusterId, namespace, types, limit, access(request));
    }

    /** OperatorWorkspaceController의 resourceContext 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Get relationships, changes and incidents for a Kubernetes resource snapshot")
    @GetMapping("/clusters/{clusterId}/resources/{kind}/{name}/context")
    public ResourceContext resourceContext(@PathVariable UUID clusterId, @PathVariable String kind,
                                           @PathVariable String name,
                                           @RequestParam(required = false) String namespace,
                                           HttpServletRequest request) {
        return service.resourceContext(clusterId, namespace, kind, name, access(request));
    }

    /** OperatorWorkspaceController의 collaboration 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Get incident collaboration metadata and linked incidents")
    @GetMapping("/incidents/{incidentId}/collaboration")
    public IncidentCollaboration collaboration(@PathVariable UUID incidentId, HttpServletRequest request) {
        return service.collaboration(incidentId, access(request));
    }

    /** OperatorWorkspaceController의 updateCollaboration 처리 대상의 상태를 갱신한다. */
    @Operation(summary = "Update incident owner, tags and response targets")
    @PatchMapping("/incidents/{incidentId}/collaboration")
    public IncidentCollaboration updateCollaboration(@PathVariable UUID incidentId,
                                                     @Valid @RequestBody CollaborationRequest body,
                                                     HttpServletRequest request) {
        return service.updateCollaboration(incidentId, body.assignee(), body.tags(), body.acknowledgeDueAt(),
                body.resolveDueAt(), actor(request), requestId(request), access(request));
    }

    /** OperatorWorkspaceController의 comment 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Append an operator comment to the incident timeline")
    @PostMapping("/incidents/{incidentId}/comments")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void comment(@PathVariable UUID incidentId, @Valid @RequestBody CommentRequest body,
                        HttpServletRequest request) {
        service.addComment(incidentId, body.comment(), actor(request), requestId(request), access(request));
    }

    /** OperatorWorkspaceController의 createIncident 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Operation(summary = "Create a manual incident from operator evidence")
    @PostMapping("/incidents/manual")
    @ResponseStatus(HttpStatus.CREATED)
    public Incident createIncident(@Valid @RequestBody ManualIncidentRequest body, HttpServletRequest request) {
        return service.createManualIncident(body.clusterId(), body.namespace(), body.resourceKind(), body.resourceName(),
                body.severity(), body.title(), body.summary(), body.nextAction(), actor(request), requestId(request), access(request));
    }

    /** OperatorWorkspaceController의 link 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Link two related incidents")
    @PostMapping("/incidents/{incidentId}/links")
    public IncidentCollaboration link(@PathVariable UUID incidentId, @Valid @RequestBody IncidentLinkRequest body,
                                      HttpServletRequest request) {
        return service.linkIncident(incidentId, body.relatedIncidentId(), body.relationType(), actor(request),
                requestId(request), access(request));
    }

    /** OperatorWorkspaceController의 unlink 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Remove a relationship between two incidents")
    @DeleteMapping("/incidents/{incidentId}/links/{relatedIncidentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@PathVariable UUID incidentId, @PathVariable UUID relatedIncidentId, HttpServletRequest request) {
        service.unlinkIncident(incidentId, relatedIncidentId, actor(request), requestId(request), access(request));
    }

    /** OperatorWorkspaceController의 merge 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Logically merge incidents while retaining source evidence")
    @PostMapping("/incidents/{incidentId}/merge")
    public IncidentCollaboration merge(@PathVariable UUID incidentId, @Valid @RequestBody MergeRequest body,
                                       HttpServletRequest request) {
        return service.merge(incidentId, body.sourceIncidentIds(), body.note(), actor(request), requestId(request), access(request));
    }

    /** OperatorWorkspaceController의 split 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Split selected evidence into a new incident")
    @PostMapping("/incidents/{incidentId}/split")
    @ResponseStatus(HttpStatus.CREATED)
    public Incident split(@PathVariable UUID incidentId, @Valid @RequestBody SplitRequest body,
                          HttpServletRequest request) {
        return service.split(incidentId, body.evidenceIds(), body.title(), body.severity(), actor(request),
                requestId(request), access(request));
    }

    /** OperatorWorkspaceController의 runbookLibrary 처리의 핵심 작업 흐름을 실행한다. */
    @Operation(summary = "List system and custom runbooks")
    @GetMapping("/runbooks/library")
    public List<ManagedRunbook> runbookLibrary(HttpServletRequest request) {
        return service.runbookLibrary(access(request));
    }

    /** OperatorWorkspaceController의 createRunbook 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Operation(summary = "Create a versioned custom runbook")
    @PostMapping("/runbooks/custom")
    @ResponseStatus(HttpStatus.CREATED)
    public ManagedRunbook createRunbook(@Valid @RequestBody RunbookRequest body, HttpServletRequest request) {
        return service.createRunbook(body.toInput(), actor(request), requestId(request), access(request));
    }

    /** OperatorWorkspaceController의 updateRunbook 처리 대상의 상태를 갱신한다. */
    @Operation(summary = "Update a custom runbook and create a new immutable version")
    @PutMapping("/runbooks/custom/{runbookId}")
    public ManagedRunbook updateRunbook(@PathVariable String runbookId, @Valid @RequestBody RunbookRequest body,
                                        HttpServletRequest request) {
        return service.updateRunbook(runbookId, body.toInput(), actor(request), requestId(request), access(request));
    }

    /** OperatorWorkspaceController의 duplicateRunbook 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Duplicate a system or custom runbook into an editable custom runbook")
    @PostMapping("/runbooks/{runbookId}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public ManagedRunbook duplicateRunbook(@PathVariable String runbookId, HttpServletRequest request) {
        return service.duplicateRunbook(runbookId, actor(request), requestId(request), access(request));
    }

    /** OperatorWorkspaceController의 setRunbookEnabled 처리 대상의 상태를 갱신한다. */
    @Operation(summary = "Enable or disable a custom runbook")
    @PatchMapping("/runbooks/custom/{runbookId}/enabled")
    public ManagedRunbook setRunbookEnabled(@PathVariable String runbookId,
                                            @Valid @RequestBody EnabledRequest body,
                                            HttpServletRequest request) {
        return service.setRunbookEnabled(runbookId, body.enabled(), actor(request), requestId(request), access(request));
    }

    /** OperatorWorkspaceController의 runbookVersions 처리의 핵심 작업 흐름을 실행한다. */
    @Operation(summary = "List immutable versions of a custom runbook")
    @GetMapping("/runbooks/custom/{runbookId}/versions")
    public List<RunbookVersion> runbookVersions(@PathVariable String runbookId, HttpServletRequest request) {
        return service.runbookVersions(runbookId, access(request));
    }

    /** OperatorWorkspaceController의 restoreRunbook 처리에 필요한 업무 로직을 수행한다. */
    @Operation(summary = "Restore a custom runbook version as a new latest version")
    @PostMapping("/runbooks/custom/{runbookId}/versions/{version}/restore")
    public ManagedRunbook restoreRunbook(@PathVariable String runbookId, @PathVariable @Min(1) int version,
                                         HttpServletRequest request) {
        return service.restoreRunbook(runbookId, version, actor(request), requestId(request), access(request));
    }

    /** OperatorWorkspaceController의 deleteRunbook 처리 대상과 관련 상태를 안전하게 정리한다. */
    @Operation(summary = "Delete a custom runbook and its immutable version history")
    @DeleteMapping("/runbooks/custom/{runbookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRunbook(@PathVariable String runbookId, HttpServletRequest request) {
        service.deleteRunbook(runbookId, actor(request), requestId(request), access(request));
    }

    /** OperatorWorkspaceController의 access 처리에 필요한 업무 로직을 수행한다. */
    private ResolvedAccess access(HttpServletRequest request) {
        return (ResolvedAccess) request.getAttribute(ApiAuthorizationInterceptor.RESOLVED_ACCESS_ATTRIBUTE);
    }

    /** OperatorWorkspaceController의 actor 처리에 필요한 업무 로직을 수행한다. */
    private String actor(HttpServletRequest request) {
        return request.getUserPrincipal() == null ? "local-operator" : request.getUserPrincipal().getName();
    }

    /** OperatorWorkspaceController의 requestId 처리에 필요한 업무 로직을 수행한다. */
    private String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(RequestAttributes.REQUEST_ID));
    }

    public record CollaborationRequest(@Size(max = 255) String assignee,
                                       @Size(max = 20) List<@Size(max = 50) String> tags,
                                       Instant acknowledgeDueAt, Instant resolveDueAt) { }

    public record CommentRequest(@NotBlank @Size(max = 2000) String comment) { }

    public record ManualIncidentRequest(@NotNull UUID clusterId, @Size(max = 255) String namespace,
                                        @Size(max = 100) String resourceKind, @Size(max = 255) String resourceName,
                                        @NotBlank String severity, @NotBlank @Size(max = 500) String title,
                                        @Size(max = 4000) String summary, @Size(max = 2000) String nextAction) { }

    public record IncidentLinkRequest(@NotNull UUID relatedIncidentId, @NotBlank @Size(max = 30) String relationType) { }

    public record MergeRequest(@NotEmpty @Size(max = 20) List<UUID> sourceIncidentIds,
                               @Size(max = 1000) String note) { }

    public record SplitRequest(@NotEmpty @Size(max = 50) List<UUID> evidenceIds,
                               @NotBlank @Size(max = 500) String title, @NotBlank String severity) { }

    public record EnabledRequest(boolean enabled) { }

    public record RunbookRequest(
            @NotBlank @Size(max = 100) String signal,
            @NotBlank @Size(max = 100) String category,
            @Size(max = 100) String resourceKind,
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 2000) String beginnerExplanation,
            @NotBlank @Size(max = 2000) String verificationCommand,
            @NotBlank @Size(max = 2000) String expectedResult,
            @Size(max = 2000) String safeAction,
            @NotBlank @Size(max = 2000) String validationCommand,
            @Size(max = 2000) String rollbackGuidance,
            @NotBlank @Size(max = 30) String safetyLevel,
            boolean enabled,
            @Size(max = 1000) String changeNote
    ) {
        /** RunbookRequest의 toInput 처리 데이터를 필요한 표현으로 변환한다. */
        RunbookInput toInput() {
            return new RunbookInput(signal, category, resourceKind, title, beginnerExplanation, verificationCommand,
                    expectedResult, safeAction, validationCommand, rollbackGuidance, safetyLevel, enabled, changeNote);
        }
    }
}
