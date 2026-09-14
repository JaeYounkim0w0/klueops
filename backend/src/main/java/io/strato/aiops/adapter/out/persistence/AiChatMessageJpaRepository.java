package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface AiChatMessageJpaRepository extends JpaRepository<AiChatMessageEntity, UUID> {

    List<AiChatMessageEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId, Pageable pageable);

    void deleteByConversationId(UUID conversationId);
}
