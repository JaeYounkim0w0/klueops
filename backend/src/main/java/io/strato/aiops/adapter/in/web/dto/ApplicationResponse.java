package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.domain.application.ApplicationDeploymentType;
import io.strato.aiops.domain.application.ApplicationStatus;
import io.strato.aiops.domain.application.ManagedApplication;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Managed application")
public record ApplicationResponse(
        UUID id,
        UUID clusterId,
        String namespace,
        String name,
        ApplicationDeploymentType deploymentType,
        String image,
        String helmReleaseName,
        String helmChart,
        ApplicationStatus status,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        Instant lastSyncedAt,
        String lastSyncStatus,
        String lastSyncError,
        Integer currentReleaseRevision,
        UUID chartVersionId,
        UUID valuesRevisionId
) {
    /** ApplicationResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
    public static ApplicationResponse from(ManagedApplication application) {
        return new ApplicationResponse(application.id(), application.clusterId(), application.namespace(), application.name(),
                application.deploymentType(), application.image(), application.helmReleaseName(), application.helmChart(),
                application.status(), application.createdBy(), application.createdAt(), application.updatedAt(),
                application.lastSyncedAt(), application.lastSyncStatus(), application.lastSyncError(),
                application.currentReleaseRevision(), application.chartVersionId(), application.valuesRevisionId());
    }
}
