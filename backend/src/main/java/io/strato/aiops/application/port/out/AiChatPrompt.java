package io.strato.aiops.application.port.out;

import java.util.UUID;

public record AiChatPrompt(
        UUID tenantId,
        UUID conversationId,
        String userMessage,
        String sanitizedContext,
        String promptVersion
) {
    public AiChatPrompt(UUID conversationId, String userMessage, String sanitizedContext, String promptVersion) {
        this(null, conversationId, userMessage, sanitizedContext, promptVersion);
    }
}
