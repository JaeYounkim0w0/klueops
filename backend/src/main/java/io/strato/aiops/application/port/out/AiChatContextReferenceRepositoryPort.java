package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.chat.AiChatContextReference;

import java.util.List;
import java.util.UUID;

public interface AiChatContextReferenceRepositoryPort {

    /** AiChatContextReferenceRepositoryPort의 saveAll 처리에 필요한 데이터를 생성하거나 저장한다. */
    List<AiChatContextReference> saveAll(List<AiChatContextReference> references);

    /** AiChatContextReferenceRepositoryPort의 findByMessageId 처리 결과를 조회해 반환한다. */
    List<AiChatContextReference> findByMessageId(UUID messageId);

    /** AiChatContextReferenceRepositoryPort의 findByConversationId 처리 결과를 조회해 반환한다. */
    List<AiChatContextReference> findByConversationId(UUID conversationId);

    /** AiChatContextReferenceRepositoryPort의 deleteByConversationId 처리 대상과 관련 상태를 안전하게 정리한다. */
    default void deleteByConversationId(UUID conversationId) {
        throw new UnsupportedOperationException("Conversation context deletion is not supported");
    }
}
