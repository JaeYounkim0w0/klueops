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

    /** JpaAiChatMessageRepositoryAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public JpaAiChatMessageRepositoryAdapter(AiChatMessageJpaRepository repository) {
        this.repository = repository;
    }

    /** JpaAiChatMessageRepositoryAdapter의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public AiChatMessage save(AiChatMessage message) {
        return repository.save(AiChatMessageEntity.fromDomain(message)).toDomain();
    }

    /** JpaAiChatMessageRepositoryAdapter의 findByConversationId 처리 결과를 조회해 반환한다. */
    @Override
    public List<AiChatMessage> findByConversationId(UUID conversationId, int limit) {
        return repository.findByConversationIdOrderByCreatedAtAsc(conversationId, PageRequest.of(0, limit)).stream()
                .map(AiChatMessageEntity::toDomain)
                .toList();
    }

    /** JpaAiChatMessageRepositoryAdapter의 findById 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<AiChatMessage> findById(UUID messageId) {
        return repository.findById(messageId).map(AiChatMessageEntity::toDomain);
    }

    /** JpaAiChatMessageRepositoryAdapter의 deleteByConversationId 처리 대상과 관련 상태를 안전하게 정리한다. */
    @Override
    public void deleteByConversationId(UUID conversationId) {
        repository.deleteByConversationId(conversationId);
    }
}
