package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.cluster.ClusterCredentialType;

public record KubernetesConnectionCredential(
        ClusterCredentialType credentialType,
        String payload
) {
}
