package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.chat.AiChatConversation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiChatConversationRepositoryPort {

    /** AiChatConversationRepositoryPort의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    AiChatConversation save(AiChatConversation conversation);

    /** AiChatConversationRepositoryPort의 findByIdAndCreatedBy 처리 결과를 조회해 반환한다. */
    Optional<AiChatConversation> findByIdAndCreatedBy(UUID conversationId, String createdBy);

    /** AiChatConversationRepositoryPort의 findRecent 처리 결과를 조회해 반환한다. */
    List<AiChatConversation> findRecent(String createdBy, boolean archived, int limit);

    /** AiChatConversationRepositoryPort의 deleteById 처리 대상과 관련 상태를 안전하게 정리한다. */
    void deleteById(UUID conversationId);
}
