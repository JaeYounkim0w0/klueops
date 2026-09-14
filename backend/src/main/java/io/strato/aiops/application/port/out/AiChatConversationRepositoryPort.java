package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.chat.AiChatConversation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiChatConversationRepositoryPort {

    AiChatConversation save(AiChatConversation conversation);

    Optional<AiChatConversation> findByIdAndCreatedBy(UUID conversationId, String createdBy);

    List<AiChatConversation> findRecent(String createdBy, boolean archived, int limit);

    void deleteById(UUID conversationId);
}
