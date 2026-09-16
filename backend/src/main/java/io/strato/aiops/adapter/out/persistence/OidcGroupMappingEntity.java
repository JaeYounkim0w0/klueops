package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.identity.AccessScope;
import io.strato.aiops.domain.identity.OidcGroupMapping;
import io.strato.aiops.domain.identity.PlatformRole;
import io.strato.aiops.domain.identity.ScopeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "oidc_group_mappings")
class OidcGroupMappingEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 500) private String issuer;
    @Column(nullable = false) private String groupValue;
    @Column(nullable = false) private UUID tenantId;
    @Enumerated(EnumType.STRING) @Column(name = "role_name", nullable = false) private PlatformRole role;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ScopeType scopeType;
    private UUID workspaceId;
    private UUID clusterId;
    private String namespace;
    @Column(nullable = false) private boolean active;
    @Column(nullable = false) private String createdBy;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    /** OidcGroupMappingEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    protected OidcGroupMappingEntity() {
    }

    /** OidcGroupMappingEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    private OidcGroupMappingEntity(OidcGroupMapping mapping) {
        id = mapping.id();
        issuer = mapping.issuer();
        groupValue = mapping.groupValue();
        tenantId = mapping.tenantId();
        role = mapping.role();
        scopeType = mapping.scope().type();
        workspaceId = mapping.scope().workspaceId();
        clusterId = mapping.scope().clusterId();
        namespace = mapping.scope().namespace();
        active = mapping.active();
        createdBy = mapping.createdBy();
        createdAt = mapping.createdAt();
        updatedAt = mapping.updatedAt();
    }

    /** OidcGroupMappingEntity의 fromDomain 처리 데이터를 필요한 표현으로 변환한다. */
    static OidcGroupMappingEntity fromDomain(OidcGroupMapping mapping) {
        return new OidcGroupMappingEntity(mapping);
    }

    /** OidcGroupMappingEntity의 toDomain 처리 데이터를 필요한 표현으로 변환한다. */
    OidcGroupMapping toDomain() {
        AccessScope scope = switch (scopeType) {
            case TENANT -> AccessScope.tenant(tenantId);
            case WORKSPACE -> AccessScope.workspace(tenantId, workspaceId);
            case CLUSTER -> AccessScope.cluster(clusterId);
            case NAMESPACE -> AccessScope.namespace(clusterId, namespace);
            case PLATFORM -> throw new IllegalStateException("Tenant group mapping cannot have platform scope");
        };
        return new OidcGroupMapping(id, issuer, groupValue, tenantId, role, scope, active, createdBy, createdAt, updatedAt);
    }
}
