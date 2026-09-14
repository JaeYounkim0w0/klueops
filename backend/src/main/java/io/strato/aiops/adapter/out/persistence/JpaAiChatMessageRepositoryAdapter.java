package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.AiChatMessageRepositoryPort;
import io.strato.aiops.domain.chat.AiChatMessage;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaAiChatMessageRepositoryAdapter implements AiChatMessageRepositoryPort {

    private final AiChatMessageJpaRepository repository;

    public JpaAiChatMessageRepositoryAdapter(AiChatMessageJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public AiChatMessage save(AiChatMessage message) {
        return repository.save(AiChatMessageEntity.fromDomain(message)).toDomain();
    }

    @Override
    public List<AiChatMessage> findByConversationId(UUID conversationId, int limit) {
        return repository.findByConversationIdOrderByCreatedAtAsc(conversationId, PageRequest.of(0, limit)).stream()
                .map(AiChatMessageEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<AiChatMessage> findById(UUID messageId) {
        return repository.findById(messageId).map(AiChatMessageEntity::toDomain);
    }

    @Override
    public void deleteByConversationId(UUID conversationId) {
        repository.deleteByConversationId(conversationId);
    }
}
