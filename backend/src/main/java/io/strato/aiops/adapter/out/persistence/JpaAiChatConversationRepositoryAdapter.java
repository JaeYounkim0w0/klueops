package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.AiChatConversationRepositoryPort;
import io.strato.aiops.domain.chat.AiChatConversation;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaAiChatConversationRepositoryAdapter implements AiChatConversationRepositoryPort {

    private final AiChatConversationJpaRepository repository;

    public JpaAiChatConversationRepositoryAdapter(AiChatConversationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public AiChatConversation save(AiChatConversation conversation) {
        return repository.save(AiChatConversationEntity.fromDomain(conversation)).toDomain();
    }

    @Override
    public Optional<AiChatConversation> findByIdAndCreatedBy(UUID conversationId, String createdBy) {
        return repository.findByIdAndCreatedBy(conversationId, createdBy).map(AiChatConversationEntity::toDomain);
    }

    @Override
    public List<AiChatConversation> findRecent(String createdBy, boolean archived, int limit) {
        List<AiChatConversationEntity> entities = archived
                ? repository.findByCreatedByAndArchivedAtIsNotNullOrderByFavoriteDescUpdatedAtDesc(createdBy, PageRequest.of(0, limit))
                : repository.findByCreatedByAndArchivedAtIsNullOrderByFavoriteDescUpdatedAtDesc(createdBy, PageRequest.of(0, limit));
        return entities.stream()
                .map(AiChatConversationEntity::toDomain)
                .toList();
    }

    @Override
    public void deleteById(UUID conversationId) {
        repository.deleteById(conversationId);
    }
}
