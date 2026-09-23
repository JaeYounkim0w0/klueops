package io.product.aiops.application.port.in;

import java.util.UUID;

public record DeployDockerApplicationCommand(
        UUID clusterId,
        String namespace,
        String name,
        String image
) {
}
