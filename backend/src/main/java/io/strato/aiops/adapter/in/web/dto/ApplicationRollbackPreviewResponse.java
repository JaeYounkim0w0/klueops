package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.ApplicationRollbackPreviewResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApplicationRollbackPreviewResponse(
        UUID applicationId,
        UUID clusterId,
        String namespace,
        String deploymentName,
        String currentRevision,
        String targetRevision,
        boolean executable,
        String reason,
        String confirmationText,
        String currentState,
        String targetState,
        List<ApplicationRollbackRevisionResponse> revisions,
        Instant plannedAt
) {
    /** ApplicationRollbackPreviewResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
    public static ApplicationRollbackPreviewResponse from(ApplicationRollbackPreviewResult result) {
        return new ApplicationRollbackPreviewResponse(result.applicationId(), result.clusterId(), result.namespace(),
                result.deploymentName(), result.currentRevision(), result.targetRevision(), result.executable(),
                result.reason(), result.confirmationText(), result.currentState(), result.targetState(),
                result.revisions().stream().map(ApplicationRollbackRevisionResponse::from).toList(), result.plannedAt());
    }
}
