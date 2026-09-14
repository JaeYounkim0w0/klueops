package io.strato.aiops.application.port.in;

import java.util.UUID;

public record DeployHelmApplicationCommand(
        UUID clusterId,
        String namespace,
        String name,
        String releaseName,
        String chart
) {
}
