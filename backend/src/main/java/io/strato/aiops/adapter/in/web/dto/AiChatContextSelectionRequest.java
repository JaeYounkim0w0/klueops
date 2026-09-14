package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.AiChatContextSelection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "AI chat context selection")
public record AiChatContextSelectionRequest(
        UUID clusterId,
        @Size(max = 253) String namespace,
        UUID applicationId,
        Boolean includeRecentEvents,
        @Size(max = 80) String resourceType,
        @Size(max = 253) String resourceName,
        Boolean includeLogs,
        Integer logLineLimit
) {
    public AiChatContextSelection toCommand() {
        int boundedLogLines = logLineLimit == null ? 80 : Math.max(10, Math.min(logLineLimit, 200));
        return new AiChatContextSelection(clusterId, namespace, applicationId, includeRecentEvents == null || includeRecentEvents,
                resourceType, resourceName, includeLogs != null && includeLogs, boundedLogLines);
    }
}
