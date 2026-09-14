package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.ApplicationRollbackRevisionResult;

import java.time.Instant;

public record ApplicationRollbackRevisionResponse(
        String revision,
        boolean current,
        String replicaSetName,
        Integer replicas,
        String image,
        String state,
        Instant createdAt
) {
    public static ApplicationRollbackRevisionResponse from(ApplicationRollbackRevisionResult result) {
        return new ApplicationRollbackRevisionResponse(result.revision(), result.current(), result.replicaSetName(),
                result.replicas(), result.image(), result.state(), result.createdAt());
    }
}
