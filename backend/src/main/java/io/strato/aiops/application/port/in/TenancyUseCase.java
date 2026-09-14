package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.tenancy.Tenant;
import io.strato.aiops.domain.tenancy.Workspace;

import java.util.List;
import java.util.UUID;

public interface TenancyUseCase {
    List<Tenant> listTenants();
    Tenant getTenant(UUID tenantId);
    Tenant createTenant(String code, String name, String description, String actor);
    List<Workspace> listWorkspaces(UUID tenantId);
    Workspace getWorkspace(UUID workspaceId);
    Workspace createWorkspace(UUID tenantId, String code, String name, String description, String actor);
}
