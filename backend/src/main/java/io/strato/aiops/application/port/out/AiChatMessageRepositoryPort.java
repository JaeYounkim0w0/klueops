package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.chat.AiChatMessage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiChatMessageRepositoryPort {

    /** AiChatMessageRepositoryPort의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    AiChatMessage save(AiChatMessage message);

    /** AiChatMessageRepositoryPort의 findByConversationId 처리 결과를 조회해 반환한다. */
    List<AiChatMessage> findByConversationId(UUID conversationId, int limit);

    /** AiChatMessageRepositoryPort의 findById 처리 결과를 조회해 반환한다. */
    default Optional<AiChatMessage> findById(UUID messageId) {
        return Optional.empty();
    }

    /** AiChatMessageRepositoryPort의 deleteByConversationId 처리 대상과 관련 상태를 안전하게 정리한다. */
    default void deleteByConversationId(UUID conversationId) {
        throw new UnsupportedOperationException("Conversation message deletion is not supported");
    }
}
