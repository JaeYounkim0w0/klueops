package io.product.aiops.application.port.out;

import io.product.aiops.domain.cluster.ClusterCredentialType;

public record KubernetesConnectionCredential(
        ClusterCredentialType credentialType,
        String payload
) {
}
