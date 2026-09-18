package io.strato.aiops.adapter.in.web;

import io.strato.aiops.adapter.in.web.security.CurrentAccessResolver;
import io.strato.aiops.application.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import io.strato.aiops.domain.identity.Capability;
import io.strato.aiops.domain.identity.FeatureKey;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HelmValuesAssistanceControllerTest {
    /** Values 편집 권한이 없으면 생성·상태·결과 모두 저장소 호출 전에 거부한다. */
    @Test void deniesEveryAsyncEndpointWithoutValuesEditPermission() {
        var access = new CurrentAccessResolver(null, null) {
            /** 권한 판단만 분리 검증하므로 OIDC provisioning은 호출하지 않는다. */
            @Override public ResolvedAccess resolve(Authentication authentication) { return null; }
        };
        var identity = new IdentityAccessService(null, null, null, null, null, null, java.time.Clock.systemUTC()) {
            /** 테스트 사용자는 Values 편집 권한이 없다. */
            @Override public boolean allowsTenant(ResolvedAccess access, Capability capability, UUID tenantId) { return false; }
        };
        var features = new TenantFeatureGuard(null) {
            /** 기능은 활성화하되 사용자 capability 거부를 검증한다. */
            @Override public void requireEnabled(UUID tenantId, FeatureKey key) { }
        };
        var controller = new HelmValuesAssistanceController(null, access, identity, features, null);
        Authentication actor = null;
        UUID tenant = UUID.randomUUID(), job = UUID.randomUUID();
        var request = new ApplicationDeliveryCatalogController.ValuesSuggestionRequest(tenant, UUID.randomUUID(), "{}", "replica 1");
        assertThatThrownBy(() -> controller.start(request, actor)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.status(job, tenant, actor)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.result(job, tenant, actor)).isInstanceOf(AccessDeniedException.class);
    }
}
