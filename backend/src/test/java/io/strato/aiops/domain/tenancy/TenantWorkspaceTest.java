package io.strato.aiops.domain.tenancy;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantWorkspaceTest {

    @Test
    void normalizesCodesAndKeepsWorkspaceInsideItsTenant() {
        Tenant tenant = Tenant.create(" Acme-Corp ", "Acme", "Customer", "admin");
        Workspace workspace = Workspace.create(tenant.id(), " Platform-Ops ", "Platform", "Operations", "admin");

        assertThat(tenant.code()).isEqualTo("acme-corp");
        assertThat(workspace.code()).isEqualTo("platform-ops");
        assertThat(workspace.tenantId()).isEqualTo(tenant.id());
        assertThat(tenant.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(workspace.status()).isEqualTo(WorkspaceStatus.ACTIVE);
    }

    @Test
    void rejectsUnsafeOrBlankCodes() {
        assertThatThrownBy(() -> Tenant.create("invalid code!", "Acme", null, "admin"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Workspace.create(UUID.randomUUID(), " ", "Ops", null, "admin"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exposesStableDefaultPlacementForExistingClusters() {
        assertThat(TenancyDefaults.TENANT_ID).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(TenancyDefaults.WORKSPACE_ID).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000002"));

        Tenant restored = new Tenant(TenancyDefaults.TENANT_ID, "default", "Default Tenant", null,
                TenantStatus.ACTIVE, "system", Instant.EPOCH, Instant.EPOCH);
        assertThat(restored.id()).isEqualTo(TenancyDefaults.TENANT_ID);
    }
}
