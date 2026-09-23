package io.product.aiops.application.port.in;

import io.product.aiops.domain.chat.AiChatContextReference;
import io.product.aiops.domain.chat.AiChatMessage;

import java.util.List;
import java.util.UUID;

public record AiChatSendMessageResult(
        UUID conversationId,
        AiChatMessage userMessage,
        AiChatMessage assistantMessage,
        List<AiChatContextReference> contextReferences
) {
}
