package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.chat.AiChatMessage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiChatMessageRepositoryPort {

    AiChatMessage save(AiChatMessage message);

    List<AiChatMessage> findByConversationId(UUID conversationId, int limit);

    default Optional<AiChatMessage> findById(UUID messageId) {
        return Optional.empty();
    }

    default void deleteByConversationId(UUID conversationId) {
        throw new UnsupportedOperationException("Conversation message deletion is not supported");
    }
}
