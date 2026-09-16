package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.tenancy.Tenant;
import io.strato.aiops.domain.tenancy.Workspace;

import java.util.List;
import java.util.UUID;

public interface TenancyUseCase {
    /** TenancyUseCase의 listTenants 처리 결과를 조회해 반환한다. */
    List<Tenant> listTenants();
    /** TenancyUseCase의 getTenant 처리 결과를 조회해 반환한다. */
    Tenant getTenant(UUID tenantId);
    /** TenancyUseCase의 createTenant 처리에 필요한 데이터를 생성하거나 저장한다. */
    Tenant createTenant(String code, String name, String description, String actor);
    /** TenancyUseCase의 listWorkspaces 처리 결과를 조회해 반환한다. */
    List<Workspace> listWorkspaces(UUID tenantId);
    /** TenancyUseCase의 getWorkspace 처리 결과를 조회해 반환한다. */
    Workspace getWorkspace(UUID workspaceId);
    /** TenancyUseCase의 createWorkspace 처리에 필요한 데이터를 생성하거나 저장한다. */
    Workspace createWorkspace(UUID tenantId, String code, String name, String description, String actor);
}
