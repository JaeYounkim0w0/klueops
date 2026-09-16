package io.strato.aiops.domain.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OidcGroupMapping(
        UUID id,
        String issuer,
        String groupValue,
        UUID tenantId,
        PlatformRole role,
        AccessScope scope,
        boolean active,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    /** OidcGroupMapping 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public OidcGroupMapping {
        Objects.requireNonNull(id, "id is required");
        issuer = required(issuer, "issuer");
        groupValue = required(groupValue, "groupValue");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(role, "role is required");
        Objects.requireNonNull(scope, "scope is required");
        Objects.requireNonNull(createdBy, "createdBy is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (scope.type() == ScopeType.PLATFORM) {
            throw new IllegalArgumentException("Tenant group mapping cannot grant platform scope");
        }
    }

    /** OidcGroupMapping의 create 처리에 필요한 데이터를 생성하거나 저장한다. */
    public static OidcGroupMapping create(String issuer, String groupValue, UUID tenantId, PlatformRole role,
                                          AccessScope scope, String actor, Instant now) {
        return new OidcGroupMapping(UUID.randomUUID(), issuer, groupValue, tenantId, role, scope, true,
                actor, now, now);
    }

    /** OidcGroupMapping의 withActive 처리에 필요한 업무 로직을 수행한다. */
    public OidcGroupMapping withActive(boolean enabled, String actor, Instant now) {
        return new OidcGroupMapping(id, issuer, groupValue, tenantId, role, scope, enabled, actor, createdAt, now);
    }

    /** OidcGroupMapping의 required 처리 입력과 현재 상태의 유효성을 검증한다. */
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
