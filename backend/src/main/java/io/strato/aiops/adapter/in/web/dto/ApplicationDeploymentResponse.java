package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.ApplicationDeploymentResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Application deployment request result")
public record ApplicationDeploymentResponse(
        ApplicationResponse application,
        UUID jobId
) {
    public static ApplicationDeploymentResponse from(ApplicationDeploymentResult result) {
        return new ApplicationDeploymentResponse(ApplicationResponse.from(result.application()), result.jobId());
    }
}
