package io.strato.aiops.adapter.in.web;

import io.strato.aiops.application.port.in.TenancyUseCase;
import io.strato.aiops.adapter.in.web.security.ApiAuthorizationInterceptor;
import io.strato.aiops.application.service.IdentityAccessService;
import io.strato.aiops.application.service.ResolvedAccess;
import io.strato.aiops.domain.identity.Capability;
import io.strato.aiops.domain.tenancy.Tenant;
import io.strato.aiops.domain.tenancy.Workspace;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Tag(name = "Tenancy", description = "Strict tenant isolation and workspace grouping APIs")
public class TenancyController {
    private final TenancyUseCase tenancy;
    private final IdentityAccessService identityAccessService;

    /** TenancyController 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public TenancyController(TenancyUseCase tenancy, IdentityAccessService identityAccessService) {
        this.tenancy = tenancy;
        this.identityAccessService = identityAccessService;
    }

    /** TenancyController의 listTenants 처리 결과를 조회해 반환한다. */
    @GetMapping("/tenants")
    @Operation(summary = "List accessible tenants")
    public List<TenantResponse> listTenants(HttpServletRequest request) {
        ResolvedAccess access = access(request);
        return tenancy.listTenants().stream()
                .filter(tenant -> access == null || identityAccessService.allowsTenant(access, Capability.CLUSTER_READ, tenant.id()))
                .map(TenantResponse::from).toList();
    }

    /** TenancyController의 createTenant 처리에 필요한 데이터를 생성하거나 저장한다. */
    @PostMapping("/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create tenant")
    public TenantResponse createTenant(@Valid @RequestBody CreateTenancyRequest request, HttpServletRequest servletRequest) {
        return TenantResponse.from(tenancy.createTenant(request.code(), request.name(), request.description(), actor(servletRequest)));
    }

    /** TenancyController의 getTenant 처리 결과를 조회해 반환한다. */
    @GetMapping("/tenants/{tenantId}")
    public TenantResponse getTenant(@PathVariable UUID tenantId) {
        return TenantResponse.from(tenancy.getTenant(tenantId));
    }

    /** TenancyController의 listWorkspaces 처리 결과를 조회해 반환한다. */
    @GetMapping("/tenants/{tenantId}/workspaces")
    @Operation(summary = "List tenant workspaces")
    public List<WorkspaceResponse> listWorkspaces(@PathVariable UUID tenantId, HttpServletRequest request) {
        ResolvedAccess access = access(request);
        return tenancy.listWorkspaces(tenantId).stream()
                .filter(workspace -> access == null || identityAccessService.allowsWorkspace(access, Capability.CLUSTER_READ,
                        workspace.tenantId(), workspace.id()))
                .map(WorkspaceResponse::from).toList();
    }

    /** TenancyController의 createWorkspace 처리에 필요한 데이터를 생성하거나 저장한다. */
    @PostMapping("/tenants/{tenantId}/workspaces")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create tenant workspace")
    public WorkspaceResponse createWorkspace(@PathVariable UUID tenantId, @Valid @RequestBody CreateTenancyRequest request,
                                             HttpServletRequest servletRequest) {
        return WorkspaceResponse.from(tenancy.createWorkspace(tenantId, request.code(), request.name(), request.description(), actor(servletRequest)));
    }

    /** TenancyController의 getWorkspace 처리 결과를 조회해 반환한다. */
    @GetMapping("/workspaces/{workspaceId}")
    public WorkspaceResponse getWorkspace(@PathVariable UUID workspaceId) {
        return WorkspaceResponse.from(tenancy.getWorkspace(workspaceId));
    }

    /** TenancyController의 actor 처리에 필요한 업무 로직을 수행한다. */
    private String actor(HttpServletRequest request) {
        return request.getUserPrincipal() == null ? "anonymous" : request.getUserPrincipal().getName();
    }

    /** TenancyController의 access 처리에 필요한 업무 로직을 수행한다. */
    private ResolvedAccess access(HttpServletRequest request) {
        return (ResolvedAccess) request.getAttribute(ApiAuthorizationInterceptor.RESOLVED_ACCESS_ATTRIBUTE);
    }

    public record CreateTenancyRequest(@NotBlank String code, @NotBlank String name, String description) {}
    public record TenantResponse(UUID id, String code, String name, String description, String status, String createdBy, Instant createdAt, Instant updatedAt) {
        /** TenantResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static TenantResponse from(Tenant value) { return new TenantResponse(value.id(), value.code(), value.name(), value.description(), value.status().name(), value.createdBy(), value.createdAt(), value.updatedAt()); }
    }
    public record WorkspaceResponse(UUID id, UUID tenantId, String code, String name, String description, String status, String createdBy, Instant createdAt, Instant updatedAt) {
        /** WorkspaceResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
        static WorkspaceResponse from(Workspace value) { return new WorkspaceResponse(value.id(), value.tenantId(), value.code(), value.name(), value.description(), value.status().name(), value.createdBy(), value.createdAt(), value.updatedAt()); }
    }
}
