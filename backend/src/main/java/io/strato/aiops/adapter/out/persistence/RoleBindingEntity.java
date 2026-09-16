package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.identity.AccessScope;
import io.strato.aiops.domain.identity.PlatformRole;
import io.strato.aiops.domain.identity.PrincipalType;
import io.strato.aiops.domain.identity.RoleBinding;
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
@Table(name = "role_bindings")
class RoleBindingEntity {
    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrincipalType principalType;
    @Column(nullable = false)
    private String principalKey;
    @Enumerated(EnumType.STRING)
    @Column(name = "role_name", nullable = false)
    private PlatformRole role;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScopeType scopeType;
    private UUID tenantId;
    private UUID workspaceId;
    private UUID clusterId;
    private String namespace;
    @Column(nullable = false)
    private String createdBy;
    @Column(nullable = false)
    private Instant createdAt;

    /** RoleBindingEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    protected RoleBindingEntity() {
    }

    /** RoleBindingEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    private RoleBindingEntity(RoleBinding binding) {
        id = binding.id();
        principalType = binding.principalType();
        principalKey = binding.principalKey();
        role = binding.role();
        scopeType = binding.scope().type();
        tenantId = binding.scope().tenantId();
        workspaceId = binding.scope().workspaceId();
        clusterId = binding.scope().clusterId();
        namespace = binding.scope().namespace();
        createdBy = binding.createdBy();
        createdAt = binding.createdAt();
    }

    /** RoleBindingEntity의 fromDomain 처리 데이터를 필요한 표현으로 변환한다. */
    static RoleBindingEntity fromDomain(RoleBinding binding) {
        return new RoleBindingEntity(binding);
    }

    /** RoleBindingEntity의 toDomain 처리 데이터를 필요한 표현으로 변환한다. */
    RoleBinding toDomain() {
        return new RoleBinding(id, principalType, principalKey, role,
                new AccessScope(scopeType, tenantId, workspaceId, clusterId, namespace),
                createdBy, createdAt);
    }
}
