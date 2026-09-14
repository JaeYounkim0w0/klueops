package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.chat.AiChatContextReference;

import java.util.List;
import java.util.UUID;

public interface AiChatContextReferenceRepositoryPort {

    List<AiChatContextReference> saveAll(List<AiChatContextReference> references);

    List<AiChatContextReference> findByMessageId(UUID messageId);

    List<AiChatContextReference> findByConversationId(UUID conversationId);

    default void deleteByConversationId(UUID conversationId) {
        throw new UnsupportedOperationException("Conversation context deletion is not supported");
    }
}
