package io.strato.aiops.domain.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RoleBinding(
        UUID id,
        PrincipalType principalType,
        String principalKey,
        PlatformRole role,
        AccessScope scope,
        String createdBy,
        Instant createdAt
) {
    /** RoleBinding 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public RoleBinding {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(principalType, "principalType is required");
        Objects.requireNonNull(principalKey, "principalKey is required");
        Objects.requireNonNull(role, "role is required");
        Objects.requireNonNull(scope, "scope is required");
        Objects.requireNonNull(createdBy, "createdBy is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
    }

    /** RoleBinding의 create 처리에 필요한 데이터를 생성하거나 저장한다. */
    public static RoleBinding create(PrincipalType principalType, String principalKey, PlatformRole role,
                                     AccessScope scope, String createdBy, Instant now) {
        return new RoleBinding(UUID.randomUUID(), principalType, principalKey.trim(), role, scope, createdBy, now);
    }
}
