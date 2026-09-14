package io.strato.aiops.application.port.out;

import java.util.UUID;

public record AiChatPrompt(
        UUID conversationId,
        String userMessage,
        String sanitizedContext,
        String promptVersion
) {
}
