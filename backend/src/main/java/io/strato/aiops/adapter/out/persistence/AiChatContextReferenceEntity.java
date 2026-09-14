package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.chat.AiChatContextReference;
import io.strato.aiops.domain.chat.AiChatReferenceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_chat_context_references")
class AiChatContextReferenceEntity {

    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID messageId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiChatReferenceType referenceType;
    private UUID referenceId;
    @Column(nullable = false)
    private String label;
    @Column(nullable = false)
    private Instant createdAt;

    protected AiChatContextReferenceEntity() {
    }

    private AiChatContextReferenceEntity(UUID id, UUID messageId, AiChatReferenceType referenceType,
                                         UUID referenceId, String label, Instant createdAt) {
        this.id = id;
        this.messageId = messageId;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.label = label;
        this.createdAt = createdAt;
    }

    static AiChatContextReferenceEntity fromDomain(AiChatContextReference reference) {
        return new AiChatContextReferenceEntity(reference.id(), reference.messageId(), reference.referenceType(),
                reference.referenceId(), reference.label(), reference.createdAt());
    }

    AiChatContextReference toDomain() {
        return new AiChatContextReference(id, messageId, referenceType, referenceId, label, createdAt);
    }
}
