package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.identity.MembershipStatus;
import io.strato.aiops.domain.identity.TenantMembership;
import io.strato.aiops.domain.identity.PlatformRole;
import io.strato.aiops.domain.identity.ScopeType;
import io.strato.aiops.domain.identity.AccessScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_memberships")
class TenantMembershipEntity {
    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    private UUID userId;
    @Column(length = 500) private String pendingIssuer;
    private String pendingSubject;
    @Column(length = 320) private String pendingEmail;
    @Enumerated(EnumType.STRING) @Column(name = "role_name", nullable = false) private PlatformRole role;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ScopeType scopeType;
    private UUID workspaceId;
    private UUID clusterId;
    private String namespace;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private MembershipStatus status;
    @Column(nullable = false) private String createdBy;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    private Instant suspendedAt;
    private Instant offboardedAt;

    /** TenantMembershipEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    protected TenantMembershipEntity() {
    }

    /** TenantMembershipEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    private TenantMembershipEntity(TenantMembership membership) {
        id = membership.id();
        tenantId = membership.tenantId();
        userId = membership.userId();
        pendingIssuer = membership.pendingIssuer();
        pendingSubject = membership.pendingSubject();
        pendingEmail = membership.pendingEmail();
        role = membership.role();
        scopeType = membership.scope().type();
        workspaceId = membership.scope().workspaceId();
        clusterId = membership.scope().clusterId();
        namespace = membership.scope().namespace();
        status = membership.status();
        createdBy = membership.createdBy();
        createdAt = membership.createdAt();
        updatedAt = membership.updatedAt();
        suspendedAt = membership.suspendedAt();
        offboardedAt = membership.offboardedAt();
    }

    /** TenantMembershipEntity의 fromDomain 처리 데이터를 필요한 표현으로 변환한다. */
    static TenantMembershipEntity fromDomain(TenantMembership membership) {
        return new TenantMembershipEntity(membership);
    }

    /** TenantMembershipEntity의 toDomain 처리 데이터를 필요한 표현으로 변환한다. */
    TenantMembership toDomain() {
        AccessScope scope = switch (scopeType) {
            case TENANT -> AccessScope.tenant(tenantId);
            case WORKSPACE -> AccessScope.workspace(tenantId, workspaceId);
            case CLUSTER -> AccessScope.cluster(clusterId);
            case NAMESPACE -> AccessScope.namespace(clusterId, namespace);
            case PLATFORM -> throw new IllegalStateException("Tenant membership cannot have platform scope");
        };
        return new TenantMembership(id, tenantId, userId, pendingIssuer, pendingSubject, pendingEmail, role, scope,
                status, createdBy, createdAt, updatedAt, suspendedAt, offboardedAt);
    }
}
