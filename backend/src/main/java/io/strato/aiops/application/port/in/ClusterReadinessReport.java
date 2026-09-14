package io.strato.aiops.application.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClusterReadinessReport(
        UUID clusterId,
        String clusterName,
        String overallStatus,
        int overallScore,
        CapabilityMatrix capabilities,
        CredentialHealth credential,
        UpgradeReadiness upgrade,
        Instant checkedAt,
        Instant cacheExpiresAt
) {
    public record CapabilityMatrix(
            String status,
            int allowedCount,
            int deniedCount,
            int unknownCount,
            int score,
            List<CapabilityCheck> checks
    ) {
    }

    public record CapabilityCheck(
            String id,
            String category,
            String displayName,
            String verb,
            String apiGroup,
            String resource,
            String namespace,
            boolean allowed,
            String state,
            String reason,
            String evidenceSource
    ) {
    }

    public record CredentialHealth(
            String status,
            String credentialType,
            boolean connectionReachable,
            String kubernetesVersion,
            Instant storedAt,
            long ageDays,
            Instant tokenExpiresAt,
            Long tokenExpiresInDays,
            Instant clientCertificateExpiresAt,
            Instant caCertificateExpiresAt,
            String encryptionAlgorithm,
            String keyId,
            boolean secretValueExposed,
            String rotationStatus,
            Instant recommendedRotationBy,
            List<String> rotationSteps,
            List<String> findings,
            List<String> recommendations,
            String connectionMessage
    ) {
    }

    public record UpgradeReadiness(
            String status,
            int score,
            String currentVersion,
            String targetVersion,
            List<NodeVersion> nodes,
            List<UpgradeFinding> findings,
            List<String> recommendedSteps,
            String compatibilityCatalogVersion,
            String evidenceSource
    ) {
    }

    public record NodeVersion(String name, String status, String kubeletVersion, int minorSkew) {
    }

    public record UpgradeFinding(
            String severity,
            String category,
            String title,
            String detail,
            String resourceRef,
            String evidence
    ) {
    }
}
