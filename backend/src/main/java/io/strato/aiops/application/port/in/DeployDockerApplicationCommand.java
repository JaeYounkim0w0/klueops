package io.strato.aiops.application.port.in;

import java.util.UUID;

public record DeployDockerApplicationCommand(
        UUID clusterId,
        String namespace,
        String name,
        String image
) {
}
