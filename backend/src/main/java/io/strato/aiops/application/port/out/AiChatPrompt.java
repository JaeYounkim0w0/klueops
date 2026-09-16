package io.strato.aiops.application.port.out;

import java.util.UUID;

public record AiChatPrompt(
        UUID tenantId,
        UUID conversationId,
        String userMessage,
        String sanitizedContext,
        String promptVersion
) {
    /** AiChatPrompt 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AiChatPrompt(UUID conversationId, String userMessage, String sanitizedContext, String promptVersion) {
        this(null, conversationId, userMessage, sanitizedContext, promptVersion);
    }
}
