package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.analysis.SupportedLocale;

import java.util.UUID;

public record SendAiChatMessageCommand(
        UUID conversationId,
        String message,
        AiChatContextSelection contextSelection,
        SupportedLocale locale
) {
    public SendAiChatMessageCommand(UUID conversationId, String message, AiChatContextSelection contextSelection) {
        this(conversationId, message, contextSelection, SupportedLocale.ENGLISH);
    }

    public SendAiChatMessageCommand {
        locale = locale == null ? SupportedLocale.ENGLISH : locale;
    }
}
