package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.DeployDockerApplicationCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Docker image application deployment request")
public record DeployDockerApplicationRequest(
        @NotNull UUID clusterId,
        @NotBlank String namespace,
        @NotBlank String name,
        @NotBlank String image
) {
    /** DeployDockerApplicationRequest의 toCommand 처리 데이터를 필요한 표현으로 변환한다. */
    public DeployDockerApplicationCommand toCommand() {
        return new DeployDockerApplicationCommand(clusterId, namespace, name, image);
    }
}
