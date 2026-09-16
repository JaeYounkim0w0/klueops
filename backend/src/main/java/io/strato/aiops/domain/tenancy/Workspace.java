package io.strato.aiops.domain.tenancy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Workspace(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String description,
        WorkspaceStatus status,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    /** Workspace 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public Workspace {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        code = TenancyCode.normalize(code);
        name = requireText(name, "name");
        Objects.requireNonNull(status, "status is required");
        createdBy = requireText(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    /** Workspace의 create 처리에 필요한 데이터를 생성하거나 저장한다. */
    public static Workspace create(UUID tenantId, String code, String name, String description, String actor) {
        Instant now = Instant.now();
        return new Workspace(UUID.randomUUID(), tenantId, code, name, description, WorkspaceStatus.ACTIVE, actor, now, now);
    }

    /** Workspace의 requireText 처리 입력과 현재 상태의 유효성을 검증한다. */
    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
