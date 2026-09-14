package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.application.ManagedApplication;

import java.util.UUID;

public record ApplicationDeploymentResult(
        ManagedApplication application,
        UUID jobId
) {
}
