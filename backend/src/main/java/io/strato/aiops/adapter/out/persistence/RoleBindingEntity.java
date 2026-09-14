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

    protected RoleBindingEntity() {
    }

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

    static RoleBindingEntity fromDomain(RoleBinding binding) {
        return new RoleBindingEntity(binding);
    }

    RoleBinding toDomain() {
        return new RoleBinding(id, principalType, principalKey, role,
                new AccessScope(scopeType, tenantId, workspaceId, clusterId, namespace),
                createdBy, createdAt);
    }
}
