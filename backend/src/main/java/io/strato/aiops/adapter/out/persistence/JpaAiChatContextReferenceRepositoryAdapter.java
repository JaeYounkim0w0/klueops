package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.AiChatContextReferenceRepositoryPort;
import io.strato.aiops.domain.chat.AiChatContextReference;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class JpaAiChatContextReferenceRepositoryAdapter implements AiChatContextReferenceRepositoryPort {

    private final AiChatContextReferenceJpaRepository repository;

    /** JpaAiChatContextReferenceRepositoryAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public JpaAiChatContextReferenceRepositoryAdapter(AiChatContextReferenceJpaRepository repository) {
        this.repository = repository;
    }

    /** JpaAiChatContextReferenceRepositoryAdapter의 saveAll 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public List<AiChatContextReference> saveAll(List<AiChatContextReference> references) {
        return repository.saveAll(references.stream()
                        .map(AiChatContextReferenceEntity::fromDomain)
                        .toList())
                .stream()
                .map(AiChatContextReferenceEntity::toDomain)
                .toList();
    }

    /** JpaAiChatContextReferenceRepositoryAdapter의 findByMessageId 처리 결과를 조회해 반환한다. */
    @Override
    public List<AiChatContextReference> findByMessageId(UUID messageId) {
        return repository.findByMessageIdOrderByCreatedAtAsc(messageId).stream()
                .map(AiChatContextReferenceEntity::toDomain)
                .toList();
    }

    /** JpaAiChatContextReferenceRepositoryAdapter의 findByConversationId 처리 결과를 조회해 반환한다. */
    @Override
    public List<AiChatContextReference> findByConversationId(UUID conversationId) {
        return repository.findByConversationId(conversationId).stream()
                .map(AiChatContextReferenceEntity::toDomain)
                .toList();
    }

    /** JpaAiChatContextReferenceRepositoryAdapter의 deleteByConversationId 처리 대상과 관련 상태를 안전하게 정리한다. */
    @Override
    public void deleteByConversationId(UUID conversationId) {
        repository.deleteByConversationId(conversationId);
    }
}
