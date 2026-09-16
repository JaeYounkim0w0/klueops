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
    /** ApplicationRollbackRevisionResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
    public static ApplicationRollbackRevisionResponse from(ApplicationRollbackRevisionResult result) {
        return new ApplicationRollbackRevisionResponse(result.revision(), result.current(), result.replicaSetName(),
                result.replicas(), result.image(), result.state(), result.createdAt());
    }
}
