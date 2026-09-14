package io.strato.aiops.adapter.in.web.dto;

import java.util.List;
import java.util.UUID;

public record CommandCapabilityResponse(
        UUID clusterId,
        String namespace,
        boolean runnerAvailable,
        String kubectlVersion,
        String executionBoundary,
        String terminalBoundary,
        boolean metricsApiAvailable,
        List<String> supportedModes,
        int maximumCommandLength,
        int maximumOutputBytes,
        int maximumUserCommands,
        int maximumUserTerminals,
        int maximumClusterCommands,
        int maximumClusterTerminals,
        int maximumUserStartsPerMinute
) {
}
