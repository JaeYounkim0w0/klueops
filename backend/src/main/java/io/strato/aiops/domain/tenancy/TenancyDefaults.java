package io.strato.aiops.domain.tenancy;

import java.util.UUID;

public final class TenancyDefaults {
    public static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private TenancyDefaults() {
    }
}
