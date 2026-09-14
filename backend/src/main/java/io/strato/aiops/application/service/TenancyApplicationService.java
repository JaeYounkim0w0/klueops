package io.strato.aiops.application.service;

import io.strato.aiops.application.port.in.TenancyUseCase;
import io.strato.aiops.application.port.out.TenantRepositoryPort;
import io.strato.aiops.application.port.out.WorkspaceRepositoryPort;
import io.strato.aiops.domain.tenancy.Tenant;
import io.strato.aiops.domain.tenancy.TenantStatus;
import io.strato.aiops.domain.tenancy.Workspace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class TenancyApplicationService implements TenancyUseCase {
    private final TenantRepositoryPort tenants;
    private final WorkspaceRepositoryPort workspaces;

    public TenancyApplicationService(TenantRepositoryPort tenants, WorkspaceRepositoryPort workspaces) {
        this.tenants = tenants;
        this.workspaces = workspaces;
    }

    @Override @Transactional(readOnly = true) public List<Tenant> listTenants() { return tenants.findAll(); }
    @Override @Transactional(readOnly = true) public Tenant getTenant(UUID tenantId) { return tenants.findById(tenantId).orElseThrow(() -> new NoSuchElementException("Tenant not found: " + tenantId)); }

    @Override
    @Transactional
    public Tenant createTenant(String code, String name, String description, String actor) {
        Tenant tenant = Tenant.create(code, name, description, actor);
        if (tenants.findByCode(tenant.code()).isPresent()) throw new IllegalArgumentException("Tenant code already exists: " + tenant.code());
        return tenants.save(tenant);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Workspace> listWorkspaces(UUID tenantId) {
        getTenant(tenantId);
        return workspaces.findByTenantId(tenantId);
    }

    @Override @Transactional(readOnly = true) public Workspace getWorkspace(UUID workspaceId) { return workspaces.findById(workspaceId).orElseThrow(() -> new NoSuchElementException("Workspace not found: " + workspaceId)); }

    @Override
    @Transactional
    public Workspace createWorkspace(UUID tenantId, String code, String name, String description, String actor) {
        Tenant tenant = getTenant(tenantId);
        if (tenant.status() != TenantStatus.ACTIVE) throw new IllegalArgumentException("Workspace cannot be created in an inactive tenant");
        Workspace workspace = Workspace.create(tenant.id(), code, name, description, actor);
        if (workspaces.findByTenantIdAndCode(tenantId, workspace.code()).isPresent()) throw new IllegalArgumentException("Workspace code already exists in tenant: " + workspace.code());
        return workspaces.save(workspace);
    }
}
