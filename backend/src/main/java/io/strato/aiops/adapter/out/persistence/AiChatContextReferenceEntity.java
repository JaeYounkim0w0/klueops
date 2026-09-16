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

    /** AiChatContextReferenceEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    protected AiChatContextReferenceEntity() {
    }

    /** AiChatContextReferenceEntity 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    private AiChatContextReferenceEntity(UUID id, UUID messageId, AiChatReferenceType referenceType,
                                         UUID referenceId, String label, Instant createdAt) {
        this.id = id;
        this.messageId = messageId;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.label = label;
        this.createdAt = createdAt;
    }

    /** AiChatContextReferenceEntity의 fromDomain 처리 데이터를 필요한 표현으로 변환한다. */
    static AiChatContextReferenceEntity fromDomain(AiChatContextReference reference) {
        return new AiChatContextReferenceEntity(reference.id(), reference.messageId(), reference.referenceType(),
                reference.referenceId(), reference.label(), reference.createdAt());
    }

    /** AiChatContextReferenceEntity의 toDomain 처리 데이터를 필요한 표현으로 변환한다. */
    AiChatContextReference toDomain() {
        return new AiChatContextReference(id, messageId, referenceType, referenceId, label, createdAt);
    }
}
