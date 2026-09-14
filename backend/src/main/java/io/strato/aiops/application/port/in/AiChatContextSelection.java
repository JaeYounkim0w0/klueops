package io.strato.aiops.application.port.in;

import java.util.UUID;

public record AiChatContextSelection(
        UUID clusterId,
        String namespace,
        UUID applicationId,
        boolean includeRecentEvents,
        String resourceType,
        String resourceName,
        boolean includeLogs,
        int logLineLimit
) {
}
