package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.analysis.SupportedLocale;

import java.util.UUID;

public record SendAiChatMessageCommand(
        UUID conversationId,
        String message,
        AiChatContextSelection contextSelection,
        SupportedLocale locale
) {
    /** SendAiChatMessageCommand 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public SendAiChatMessageCommand(UUID conversationId, String message, AiChatContextSelection contextSelection) {
        this(conversationId, message, contextSelection, SupportedLocale.ENGLISH);
    }

    /** SendAiChatMessageCommand 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public SendAiChatMessageCommand {
        locale = locale == null ? SupportedLocale.ENGLISH : locale;
    }
}
