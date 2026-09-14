package io.strato.aiops.application.port.in;

import java.time.Instant;

public record ApplicationRollbackRevisionResult(
        String revision,
        boolean current,
        String replicaSetName,
        Integer replicas,
        String image,
        String state,
        Instant createdAt
) {
}
