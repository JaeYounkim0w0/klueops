package io.strato.aiops.domain.applicationdelivery;

import java.time.Instant;
import java.util.UUID;

public record DeploymentPlan(UUID id, UUID applicationId, UUID clusterId, UUID chartVersionId, UUID valuesRevisionId,
                             String namespace, String releaseName, boolean createNamespace, String exposureType, String hostname,
                             String exposurePath, String backendServiceName, Integer backendServicePort,
                             String gatewayName, String gatewayNamespace,
                             String renderedManifest, String manifestSha256, String warningsJson,
                             String confirmationText, String createdBy, Instant createdAt,
                             Instant expiresAt, Instant consumedAt) {

    public boolean executableAt(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }
}
