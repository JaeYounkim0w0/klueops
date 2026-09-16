package io.strato.aiops.domain.tenancy;

import java.util.UUID;

public final class TenancyDefaults {
    public static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    /** TenancyDefaults 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    private TenancyDefaults() {
    }
}
