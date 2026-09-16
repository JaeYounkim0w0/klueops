package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AiChatConversationJpaRepository extends JpaRepository<AiChatConversationEntity, UUID> {

    /** AiChatConversationJpaRepository의 findByIdAndCreatedBy 처리 결과를 조회해 반환한다. */
    Optional<AiChatConversationEntity> findByIdAndCreatedBy(UUID id, String createdBy);

    /** AiChatConversationJpaRepository의 findByCreatedByAndArchivedAtIsNullOrderByFavoriteDescUpdatedAtDesc 처리 결과를 조회해 반환한다. */
    List<AiChatConversationEntity> findByCreatedByAndArchivedAtIsNullOrderByFavoriteDescUpdatedAtDesc(String createdBy, Pageable pageable);

    /** AiChatConversationJpaRepository의 findByCreatedByAndArchivedAtIsNotNullOrderByFavoriteDescUpdatedAtDesc 처리 결과를 조회해 반환한다. */
    List<AiChatConversationEntity> findByCreatedByAndArchivedAtIsNotNullOrderByFavoriteDescUpdatedAtDesc(String createdBy, Pageable pageable);
}
