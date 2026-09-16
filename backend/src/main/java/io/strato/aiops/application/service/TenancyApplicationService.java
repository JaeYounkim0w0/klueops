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

    /** TenancyApplicationService 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public TenancyApplicationService(TenantRepositoryPort tenants, WorkspaceRepositoryPort workspaces) {
        this.tenants = tenants;
        this.workspaces = workspaces;
    }

    /** TenancyApplicationService의 listTenants 처리 결과를 조회해 반환한다. */
    @Override @Transactional(readOnly = true) public List<Tenant> listTenants() { return tenants.findAll(); }
    /** TenancyApplicationService의 getTenant 처리 결과를 조회해 반환한다. */
    @Override @Transactional(readOnly = true) public Tenant getTenant(UUID tenantId) { return tenants.findById(tenantId).orElseThrow(() -> new NoSuchElementException("Tenant not found: " + tenantId)); }

    /** TenancyApplicationService의 createTenant 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    @Transactional
    public Tenant createTenant(String code, String name, String description, String actor) {
        Tenant tenant = Tenant.create(code, name, description, actor);
        if (tenants.findByCode(tenant.code()).isPresent()) throw new IllegalArgumentException("Tenant code already exists: " + tenant.code());
        return tenants.save(tenant);
    }

    /** TenancyApplicationService의 listWorkspaces 처리 결과를 조회해 반환한다. */
    @Override
    @Transactional(readOnly = true)
    public List<Workspace> listWorkspaces(UUID tenantId) {
        getTenant(tenantId);
        return workspaces.findByTenantId(tenantId);
    }

    /** TenancyApplicationService의 getWorkspace 처리 결과를 조회해 반환한다. */
    @Override @Transactional(readOnly = true) public Workspace getWorkspace(UUID workspaceId) { return workspaces.findById(workspaceId).orElseThrow(() -> new NoSuchElementException("Workspace not found: " + workspaceId)); }

    /** TenancyApplicationService의 createWorkspace 처리에 필요한 데이터를 생성하거나 저장한다. */
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
