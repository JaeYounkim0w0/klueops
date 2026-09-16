package io.strato.aiops.domain.applicationdelivery;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ChartVersion(
        UUID id,
        UUID tenantChartId,
        String chartVersion,
        String appVersion,
        String sourceReference,
        String digestSha256,
        ChartTrustStatus provenanceStatus,
        UUID artifactId,
        String metadataJson,
        String importedBy,
        Instant importedAt
) {
    /** ChartVersion 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public ChartVersion {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantChartId, "tenantChartId is required");
        Objects.requireNonNull(artifactId, "artifactId is required");
        Objects.requireNonNull(provenanceStatus, "provenanceStatus is required");
        Objects.requireNonNull(importedAt, "importedAt is required");
    }
}
