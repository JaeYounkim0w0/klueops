package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.domain.chat.AiChatConversation;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "AI chat conversation")
public record AiChatConversationResponse(
        UUID id,
        String title,
        String mode,
        boolean favorite,
        UUID clusterId,
        String namespace,
        UUID applicationId,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt
) {
    /** AiChatConversationResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
    public static AiChatConversationResponse from(AiChatConversation conversation) {
        return new AiChatConversationResponse(conversation.id(), conversation.title(), conversation.mode().name(), conversation.favorite(), conversation.clusterId(),
                conversation.namespace(), conversation.applicationId(), conversation.createdBy(), conversation.createdAt(),
                conversation.updatedAt(), conversation.archivedAt());
    }
}
