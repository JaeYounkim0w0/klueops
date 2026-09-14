package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.chat.AiChatContextReference;
import io.strato.aiops.domain.chat.AiChatMessage;

import java.util.List;
import java.util.UUID;

public record AiChatSendMessageResult(
        UUID conversationId,
        AiChatMessage userMessage,
        AiChatMessage assistantMessage,
        List<AiChatContextReference> contextReferences
) {
}
