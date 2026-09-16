package io.strato.aiops.domain.applicationdelivery;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TenantChart(
        UUID id,
        UUID tenantId,
        String name,
        String description,
        ChartSourceType sourceType,
        String sourceName,
        String providerName,
        String repositoryUrl,
        String packageName,
        ChartTrustStatus trustStatus,
        Instant archivedAt,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    /** TenantChart 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public TenantChart {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        name = required(name, "name");
        packageName = required(packageName, "packageName");
        Objects.requireNonNull(sourceType, "sourceType is required");
        Objects.requireNonNull(trustStatus, "trustStatus is required");
        Objects.requireNonNull(createdBy, "createdBy is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    /** TenantChart의 create 처리에 필요한 데이터를 생성하거나 저장한다. */
    public static TenantChart create(UUID tenantId, String name, String description, ChartSourceType sourceType,
                                     String sourceName, String providerName, String repositoryUrl, String packageName,
                                     ChartTrustStatus trustStatus, String actor, Instant now) {
        return new TenantChart(UUID.randomUUID(), tenantId, name, description, sourceType, sourceName, providerName, repositoryUrl,
                packageName, trustStatus, null, actor, now, now);
    }

    /** TenantChart의 archived 처리에 필요한 업무 로직을 수행한다. */
    public TenantChart archived(Instant now) {
        return new TenantChart(id, tenantId, name, description, sourceType, sourceName, providerName, repositoryUrl, packageName,
                trustStatus, now, createdBy, createdAt, now);
    }

    /** TenantChart의 restored 처리에 필요한 업무 로직을 수행한다. */
    public TenantChart restored(Instant now) {
        return new TenantChart(id, tenantId, name, description, sourceType, sourceName, providerName, repositoryUrl, packageName,
                trustStatus, null, createdBy, createdAt, now);
    }

    /** TenantChart의 required 처리 입력과 현재 상태의 유효성을 검증한다. */
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
