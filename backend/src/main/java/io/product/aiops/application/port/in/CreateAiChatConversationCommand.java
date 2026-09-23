package io.product.aiops.application.port.in;

import io.product.aiops.domain.chat.AiChatMode;

import java.util.UUID;

public record CreateAiChatConversationCommand(
        String title,
        AiChatMode mode,
        UUID clusterId,
        String namespace,
        UUID applicationId
) {
}
