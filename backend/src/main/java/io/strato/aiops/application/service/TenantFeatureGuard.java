package io.strato.aiops.application.service;

import io.strato.aiops.domain.identity.FeatureKey;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 메뉴 숨김만으로는 직접 API 호출을 막을 수 없으므로 Tenant 기능 정책을 서버 경계에서도 강제한다.
 */
@Service
public class TenantFeatureGuard {
    private final TenantAccessAdministrationService tenantAccessService;

    public TenantFeatureGuard(TenantAccessAdministrationService tenantAccessService) {
        this.tenantAccessService = tenantAccessService;
    }

    public void requireEnabled(UUID tenantId, FeatureKey featureKey) {
        boolean enabled = tenantAccessService.featurePolicy(tenantId).getOrDefault(featureKey, true);
        if (!enabled) {
            throw new AccessDeniedException(featureKey.name() + " feature is disabled for this tenant");
        }
    }
}
