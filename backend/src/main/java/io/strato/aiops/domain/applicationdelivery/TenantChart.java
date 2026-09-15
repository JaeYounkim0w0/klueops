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
        String repositoryUrl,
        String packageName,
        ChartTrustStatus trustStatus,
        Instant archivedAt,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
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

    public static TenantChart create(UUID tenantId, String name, String description, ChartSourceType sourceType,
                                     String sourceName, String repositoryUrl, String packageName,
                                     ChartTrustStatus trustStatus, String actor, Instant now) {
        return new TenantChart(UUID.randomUUID(), tenantId, name, description, sourceType, sourceName, repositoryUrl,
                packageName, trustStatus, null, actor, now, now);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
