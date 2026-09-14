package io.strato.aiops.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AiChatConversationJpaRepository extends JpaRepository<AiChatConversationEntity, UUID> {

    Optional<AiChatConversationEntity> findByIdAndCreatedBy(UUID id, String createdBy);

    List<AiChatConversationEntity> findByCreatedByAndArchivedAtIsNullOrderByFavoriteDescUpdatedAtDesc(String createdBy, Pageable pageable);

    List<AiChatConversationEntity> findByCreatedByAndArchivedAtIsNotNullOrderByFavoriteDescUpdatedAtDesc(String createdBy, Pageable pageable);
}
