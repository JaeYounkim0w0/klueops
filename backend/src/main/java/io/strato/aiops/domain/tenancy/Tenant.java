package io.strato.aiops.domain.tenancy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Tenant(
        UUID id,
        String code,
        String name,
        String description,
        TenantStatus status,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    /** Tenant 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public Tenant {
        Objects.requireNonNull(id, "id is required");
        code = TenancyCode.normalize(code);
        name = requireText(name, "name");
        Objects.requireNonNull(status, "status is required");
        createdBy = requireText(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    /** Tenant의 create 처리에 필요한 데이터를 생성하거나 저장한다. */
    public static Tenant create(String code, String name, String description, String actor) {
        Instant now = Instant.now();
        return new Tenant(UUID.randomUUID(), code, name, description, TenantStatus.ACTIVE, actor, now, now);
    }

    /** Tenant의 requireText 처리 입력과 현재 상태의 유효성을 검증한다. */
    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
