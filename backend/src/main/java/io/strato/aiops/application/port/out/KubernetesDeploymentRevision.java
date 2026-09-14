package io.strato.aiops.application.port.out;

import java.time.Instant;

public record KubernetesDeploymentRevision(
        String revision,
        boolean current,
        String replicaSetName,
        Integer replicas,
        String image,
        String state,
        Instant createdAt
) {
}
