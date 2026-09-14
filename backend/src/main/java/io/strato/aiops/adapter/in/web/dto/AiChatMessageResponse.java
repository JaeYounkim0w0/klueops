package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.domain.chat.AiChatMessage;
import io.strato.aiops.domain.chat.AiChatRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "AI chat message")
public record AiChatMessageResponse(
        UUID id,
        UUID conversationId,
        AiChatRole role,
        String content,
        String model,
        String promptVersion,
        String finishReason,
        Long latencyMs,
        Long firstTokenLatencyMs,
        Long totalLatencyMs,
        Integer contextChars,
        String errorCode,
        String errorMessage,
        String createdBy,
        Instant createdAt
) {
    public static AiChatMessageResponse from(AiChatMessage message) {
        return new AiChatMessageResponse(message.id(), message.conversationId(), message.role(), message.content(),
                message.model(), message.promptVersion(), message.finishReason(), message.latencyMs(),
                message.firstTokenLatencyMs(), message.totalLatencyMs(), message.contextChars(),
                message.errorCode(), message.errorMessage(), message.createdBy(), message.createdAt());
    }
}
