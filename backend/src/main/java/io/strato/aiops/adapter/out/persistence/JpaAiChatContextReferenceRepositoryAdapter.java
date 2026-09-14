package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.AiChatContextReferenceRepositoryPort;
import io.strato.aiops.domain.chat.AiChatContextReference;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class JpaAiChatContextReferenceRepositoryAdapter implements AiChatContextReferenceRepositoryPort {

    private final AiChatContextReferenceJpaRepository repository;

    public JpaAiChatContextReferenceRepositoryAdapter(AiChatContextReferenceJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<AiChatContextReference> saveAll(List<AiChatContextReference> references) {
        return repository.saveAll(references.stream()
                        .map(AiChatContextReferenceEntity::fromDomain)
                        .toList())
                .stream()
                .map(AiChatContextReferenceEntity::toDomain)
                .toList();
    }

    @Override
    public List<AiChatContextReference> findByMessageId(UUID messageId) {
        return repository.findByMessageIdOrderByCreatedAtAsc(messageId).stream()
                .map(AiChatContextReferenceEntity::toDomain)
                .toList();
    }

    @Override
    public List<AiChatContextReference> findByConversationId(UUID conversationId) {
        return repository.findByConversationId(conversationId).stream()
                .map(AiChatContextReferenceEntity::toDomain)
                .toList();
    }

    @Override
    public void deleteByConversationId(UUID conversationId) {
        repository.deleteByConversationId(conversationId);
    }
}
