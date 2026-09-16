package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface AiChatContextReferenceJpaRepository extends JpaRepository<AiChatContextReferenceEntity, UUID> {

    /** AiChatContextReferenceJpaRepository의 findByMessageIdOrderByCreatedAtAsc 처리 결과를 조회해 반환한다. */
    List<AiChatContextReferenceEntity> findByMessageIdOrderByCreatedAtAsc(UUID messageId);

    /** AiChatContextReferenceJpaRepository의 findByConversationId 처리 결과를 조회해 반환한다. */
    @Query("select reference from AiChatContextReferenceEntity reference " +
            "where reference.messageId in (select message.id from AiChatMessageEntity message " +
            "where message.conversationId = :conversationId) order by reference.createdAt asc")
    List<AiChatContextReferenceEntity> findByConversationId(@Param("conversationId") UUID conversationId);

    /** AiChatContextReferenceJpaRepository의 deleteByConversationId 처리 대상과 관련 상태를 안전하게 정리한다. */
    @Modifying
    @Query("delete from AiChatContextReferenceEntity reference where reference.messageId in " +
            "(select message.id from AiChatMessageEntity message where message.conversationId = :conversationId)")
    void deleteByConversationId(@Param("conversationId") UUID conversationId);
}
