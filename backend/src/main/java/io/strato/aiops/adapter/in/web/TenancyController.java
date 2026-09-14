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

    public TenancyController(TenancyUseCase tenancy, IdentityAccessService identityAccessService) {
        this.tenancy = tenancy;
        this.identityAccessService = identityAccessService;
    }

    @GetMapping("/tenants")
    @Operation(summary = "List accessible tenants")
    public List<TenantResponse> listTenants(HttpServletRequest request) {
        ResolvedAccess access = access(request);
        return tenancy.listTenants().stream()
                .filter(tenant -> access == null || identityAccessService.allowsTenant(access, Capability.CLUSTER_READ, tenant.id()))
                .map(TenantResponse::from).toList();
    }

    @PostMapping("/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create tenant")
    public TenantResponse createTenant(@Valid @RequestBody CreateTenancyRequest request, HttpServletRequest servletRequest) {
        return TenantResponse.from(tenancy.createTenant(request.code(), request.name(), request.description(), actor(servletRequest)));
    }

    @GetMapping("/tenants/{tenantId}")
    public TenantResponse getTenant(@PathVariable UUID tenantId) {
        return TenantResponse.from(tenancy.getTenant(tenantId));
    }

    @GetMapping("/tenants/{tenantId}/workspaces")
    @Operation(summary = "List tenant workspaces")
    public List<WorkspaceResponse> listWorkspaces(@PathVariable UUID tenantId, HttpServletRequest request) {
        ResolvedAccess access = access(request);
        return tenancy.listWorkspaces(tenantId).stream()
                .filter(workspace -> access == null || identityAccessService.allowsWorkspace(access, Capability.CLUSTER_READ,
                        workspace.tenantId(), workspace.id()))
                .map(WorkspaceResponse::from).toList();
    }

    @PostMapping("/tenants/{tenantId}/workspaces")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create tenant workspace")
    public WorkspaceResponse createWorkspace(@PathVariable UUID tenantId, @Valid @RequestBody CreateTenancyRequest request,
                                             HttpServletRequest servletRequest) {
        return WorkspaceResponse.from(tenancy.createWorkspace(tenantId, request.code(), request.name(), request.description(), actor(servletRequest)));
    }

    @GetMapping("/workspaces/{workspaceId}")
    public WorkspaceResponse getWorkspace(@PathVariable UUID workspaceId) {
        return WorkspaceResponse.from(tenancy.getWorkspace(workspaceId));
    }

    private String actor(HttpServletRequest request) {
        return request.getUserPrincipal() == null ? "anonymous" : request.getUserPrincipal().getName();
    }

    private ResolvedAccess access(HttpServletRequest request) {
        return (ResolvedAccess) request.getAttribute(ApiAuthorizationInterceptor.RESOLVED_ACCESS_ATTRIBUTE);
    }

    public record CreateTenancyRequest(@NotBlank String code, @NotBlank String name, String description) {}
    public record TenantResponse(UUID id, String code, String name, String description, String status, String createdBy, Instant createdAt, Instant updatedAt) {
        static TenantResponse from(Tenant value) { return new TenantResponse(value.id(), value.code(), value.name(), value.description(), value.status().name(), value.createdBy(), value.createdAt(), value.updatedAt()); }
    }
    public record WorkspaceResponse(UUID id, UUID tenantId, String code, String name, String description, String status, String createdBy, Instant createdAt, Instant updatedAt) {
        static WorkspaceResponse from(Workspace value) { return new WorkspaceResponse(value.id(), value.tenantId(), value.code(), value.name(), value.description(), value.status().name(), value.createdBy(), value.createdAt(), value.updatedAt()); }
    }
}
