package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface AiChatContextReferenceJpaRepository extends JpaRepository<AiChatContextReferenceEntity, UUID> {

    List<AiChatContextReferenceEntity> findByMessageIdOrderByCreatedAtAsc(UUID messageId);

    @Query("select reference from AiChatContextReferenceEntity reference " +
            "where reference.messageId in (select message.id from AiChatMessageEntity message " +
            "where message.conversationId = :conversationId) order by reference.createdAt asc")
    List<AiChatContextReferenceEntity> findByConversationId(@Param("conversationId") UUID conversationId);

    @Modifying
    @Query("delete from AiChatContextReferenceEntity reference where reference.messageId in " +
            "(select message.id from AiChatMessageEntity message where message.conversationId = :conversationId)")
    void deleteByConversationId(@Param("conversationId") UUID conversationId);
}
