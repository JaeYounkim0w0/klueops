package io.strato.aiops.domain.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TenantMembership(
        UUID id,
        UUID tenantId,
        UUID userId,
        String pendingIssuer,
        String pendingSubject,
        String pendingEmail,
        PlatformRole role,
        AccessScope scope,
        MembershipStatus status,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        Instant suspendedAt,
        Instant offboardedAt
) {
    /** TenantMembership 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public TenantMembership {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(role, "role is required");
        Objects.requireNonNull(scope, "scope is required");
        Objects.requireNonNull(createdBy, "createdBy is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (userId == null && blank(pendingSubject) && blank(pendingEmail)) {
            throw new IllegalArgumentException("A linked user or pending identity is required");
        }
    }

    /** TenantMembership의 invite 처리에 필요한 업무 로직을 수행한다. */
    public static TenantMembership invite(UUID tenantId, String issuer, String subject, String email,
                                          PlatformRole role, AccessScope scope, String actor, Instant now) {
        return new TenantMembership(UUID.randomUUID(), tenantId, null, trim(issuer), trim(subject), trim(email),
                role, scope, MembershipStatus.INVITED, actor, now, now, null, null);
    }

    /** TenantMembership의 activate 처리에 필요한 업무 로직을 수행한다. */
    public TenantMembership activate(UUID linkedUserId, Instant now) {
        return new TenantMembership(id, tenantId, Objects.requireNonNull(linkedUserId), pendingIssuer, pendingSubject,
                pendingEmail, role, scope, MembershipStatus.ACTIVE, createdBy, createdAt, now, null, null);
    }

    /** TenantMembership의 suspend 처리에 필요한 업무 로직을 수행한다. */
    public TenantMembership suspend(Instant now) {
        return new TenantMembership(id, tenantId, userId, pendingIssuer, pendingSubject, pendingEmail, role, scope,
                MembershipStatus.SUSPENDED, createdBy, createdAt, now, now, null);
    }

    /** TenantMembership의 reactivate 처리에 필요한 업무 로직을 수행한다. */
    public TenantMembership reactivate(Instant now) {
        return new TenantMembership(id, tenantId, userId, pendingIssuer, pendingSubject, pendingEmail, role, scope,
                MembershipStatus.ACTIVE, createdBy, createdAt, now, null, null);
    }

    /** TenantMembership의 offboard 처리에 필요한 업무 로직을 수행한다. */
    public TenantMembership offboard(Instant now) {
        return new TenantMembership(id, tenantId, userId, pendingIssuer, pendingSubject, pendingEmail, role, scope,
                MembershipStatus.OFFBOARDED, createdBy, createdAt, now, suspendedAt, now);
    }

    /** TenantMembership의 blank 처리에 필요한 업무 로직을 수행한다. */
    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /** TenantMembership의 trim 처리에 필요한 업무 로직을 수행한다. */
    private static String trim(String value) {
        return blank(value) ? null : value.trim();
    }
}
