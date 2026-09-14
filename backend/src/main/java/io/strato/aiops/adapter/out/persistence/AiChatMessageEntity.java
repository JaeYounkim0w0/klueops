package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.domain.chat.AiChatMessage;
import io.strato.aiops.domain.chat.AiChatRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_chat_messages")
class AiChatMessageEntity {

    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID conversationId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiChatRole role;
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    private String model;
    private String promptVersion;
    private String finishReason;
    private Long latencyMs;
    private Long firstTokenLatencyMs;
    private Long totalLatencyMs;
    private Integer contextChars;
    private String errorCode;
    @Column(length = 1000)
    private String errorMessage;
    @Column(nullable = false)
    private String createdBy;
    @Column(nullable = false)
    private Instant createdAt;

    protected AiChatMessageEntity() {
    }

    private AiChatMessageEntity(UUID id, UUID conversationId, AiChatRole role, String content, String model,
                                String promptVersion, String finishReason, Long latencyMs, Long firstTokenLatencyMs,
                                Long totalLatencyMs, Integer contextChars, String errorCode,
                                String errorMessage, String createdBy, Instant createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.model = model;
        this.promptVersion = promptVersion;
        this.finishReason = finishReason;
        this.latencyMs = latencyMs;
        this.firstTokenLatencyMs = firstTokenLatencyMs;
        this.totalLatencyMs = totalLatencyMs;
        this.contextChars = contextChars;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    static AiChatMessageEntity fromDomain(AiChatMessage message) {
        return new AiChatMessageEntity(message.id(), message.conversationId(), message.role(), message.content(),
                message.model(), message.promptVersion(), message.finishReason(), message.latencyMs(),
                message.firstTokenLatencyMs(), message.totalLatencyMs(), message.contextChars(),
                message.errorCode(), message.errorMessage(), message.createdBy(), message.createdAt());
    }

    AiChatMessage toDomain() {
        return new AiChatMessage(id, conversationId, role, content, model, promptVersion, finishReason,
                latencyMs, firstTokenLatencyMs, totalLatencyMs, contextChars, errorCode, errorMessage,
                createdBy, createdAt);
    }
}
