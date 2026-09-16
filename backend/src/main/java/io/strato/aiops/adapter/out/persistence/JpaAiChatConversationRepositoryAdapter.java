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

    /** JpaAiChatConversationRepositoryAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public JpaAiChatConversationRepositoryAdapter(AiChatConversationJpaRepository repository) {
        this.repository = repository;
    }

    /** JpaAiChatConversationRepositoryAdapter의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public AiChatConversation save(AiChatConversation conversation) {
        return repository.save(AiChatConversationEntity.fromDomain(conversation)).toDomain();
    }

    /** JpaAiChatConversationRepositoryAdapter의 findByIdAndCreatedBy 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<AiChatConversation> findByIdAndCreatedBy(UUID conversationId, String createdBy) {
        return repository.findByIdAndCreatedBy(conversationId, createdBy).map(AiChatConversationEntity::toDomain);
    }

    /** JpaAiChatConversationRepositoryAdapter의 findRecent 처리 결과를 조회해 반환한다. */
    @Override
    public List<AiChatConversation> findRecent(String createdBy, boolean archived, int limit) {
        List<AiChatConversationEntity> entities = archived
                ? repository.findByCreatedByAndArchivedAtIsNotNullOrderByFavoriteDescUpdatedAtDesc(createdBy, PageRequest.of(0, limit))
                : repository.findByCreatedByAndArchivedAtIsNullOrderByFavoriteDescUpdatedAtDesc(createdBy, PageRequest.of(0, limit));
        return entities.stream()
                .map(AiChatConversationEntity::toDomain)
                .toList();
    }

    /** JpaAiChatConversationRepositoryAdapter의 deleteById 처리 대상과 관련 상태를 안전하게 정리한다. */
    @Override
    public void deleteById(UUID conversationId) {
        repository.deleteById(conversationId);
    }
}
