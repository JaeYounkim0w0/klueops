package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.chat.AiChatConversation;
import io.strato.aiops.domain.chat.AiChatMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_chat_conversations")
class AiChatConversationEntity {

    @Id
    private UUID id;
    @Column(nullable = false)
    private String title;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiChatMode chatMode;
    @Column(nullable = false)
    private boolean favorite;
    private UUID clusterId;
    private String namespace;
    private UUID applicationId;
    @Column(nullable = false)
    private String createdBy;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    private Instant archivedAt;

    protected AiChatConversationEntity() {
    }

    private AiChatConversationEntity(UUID id, String title, AiChatMode chatMode, boolean favorite, UUID clusterId, String namespace, UUID applicationId,
                                     String createdBy, Instant createdAt, Instant updatedAt, Instant archivedAt) {
        this.id = id;
        this.title = title;
        this.chatMode = chatMode;
        this.favorite = favorite;
        this.clusterId = clusterId;
        this.namespace = namespace;
        this.applicationId = applicationId;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.archivedAt = archivedAt;
    }

    static AiChatConversationEntity fromDomain(AiChatConversation conversation) {
        return new AiChatConversationEntity(conversation.id(), conversation.title(), conversation.mode(), conversation.favorite(), conversation.clusterId(),
                conversation.namespace(), conversation.applicationId(), conversation.createdBy(), conversation.createdAt(),
                conversation.updatedAt(), conversation.archivedAt());
    }

    AiChatConversation toDomain() {
        return new AiChatConversation(id, title, chatMode, favorite, clusterId, namespace, applicationId, createdBy, createdAt, updatedAt, archivedAt);
    }
}
