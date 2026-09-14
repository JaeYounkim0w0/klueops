package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.DeployHelmApplicationCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Helm chart application deployment request")
public record DeployHelmApplicationRequest(
        @NotNull UUID clusterId,
        @NotBlank String namespace,
        @NotBlank String name,
        @NotBlank String releaseName,
        @NotBlank String chart
) {
    public DeployHelmApplicationCommand toCommand() {
        return new DeployHelmApplicationCommand(clusterId, namespace, name, releaseName, chart);
    }
}
