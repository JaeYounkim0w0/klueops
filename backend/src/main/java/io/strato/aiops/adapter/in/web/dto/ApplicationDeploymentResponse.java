package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.ApplicationDeploymentResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Application deployment request result")
public record ApplicationDeploymentResponse(
        ApplicationResponse application,
        UUID jobId
) {
    /** ApplicationDeploymentResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
    public static ApplicationDeploymentResponse from(ApplicationDeploymentResult result) {
        return new ApplicationDeploymentResponse(ApplicationResponse.from(result.application()), result.jobId());
    }
}
