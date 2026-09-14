package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.cluster.ClusterCredentialType;

import java.time.Instant;
import java.util.UUID;

public record ClusterCredentialResult(
        UUID clusterId,
        ClusterCredentialType credentialType,
        String payload,
        boolean revealed,
        boolean masked,
        Instant updatedAt
) {
}
