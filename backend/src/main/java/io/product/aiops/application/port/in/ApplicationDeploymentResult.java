package io.product.aiops.application.port.in;

import io.product.aiops.domain.application.ManagedApplication;

import java.util.UUID;

public record ApplicationDeploymentResult(
        ManagedApplication application,
        UUID jobId
) {
}
