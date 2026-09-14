package io.strato.aiops.application.port.out;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public record KubectlRunRequest(
        UUID executionId,
        KubernetesConnectionCredential credential,
        String namespace,
        List<String> arguments,
        String manifest,
        Duration timeout,
        int maximumOutputBytes
) {
}

