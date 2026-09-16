package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.identity.FeatureKey;
import io.strato.aiops.domain.identity.TenantFeaturePolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_feature_policies")
class TenantFeaturePolicyEntity {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private FeatureKey featureKey;
    @Column(nullable = false) private boolean enabled;
    @Column(nullable = false) private String updatedBy;
    @Column(nullable = false) private Instant updatedAt;

    /** TenantFeaturePolicyEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    protected TenantFeaturePolicyEntity() {
    }

    /** TenantFeaturePolicyEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    private TenantFeaturePolicyEntity(TenantFeaturePolicy policy) {
        id = policy.id();
        tenantId = policy.tenantId();
        featureKey = policy.featureKey();
        enabled = policy.enabled();
        updatedBy = policy.updatedBy();
        updatedAt = policy.updatedAt();
    }

    /** TenantFeaturePolicyEntity의 fromDomain 처리 데이터를 필요한 표현으로 변환한다. */
    static TenantFeaturePolicyEntity fromDomain(TenantFeaturePolicy policy) {
        return new TenantFeaturePolicyEntity(policy);
    }

    /** TenantFeaturePolicyEntity의 toDomain 처리 데이터를 필요한 표현으로 변환한다. */
    TenantFeaturePolicy toDomain() {
        return new TenantFeaturePolicy(id, tenantId, featureKey, enabled, updatedBy, updatedAt);
    }
}
