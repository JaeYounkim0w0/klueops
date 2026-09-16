package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface AiChatMessageJpaRepository extends JpaRepository<AiChatMessageEntity, UUID> {

    /** AiChatMessageJpaRepository의 findByConversationIdOrderByCreatedAtAsc 처리 결과를 조회해 반환한다. */
    List<AiChatMessageEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId, Pageable pageable);

    /** AiChatMessageJpaRepository의 deleteByConversationId 처리 대상과 관련 상태를 안전하게 정리한다. */
    void deleteByConversationId(UUID conversationId);
}
