package io.strato.aiops.domain.chat;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AiChatContextReference(
        UUID id,
        UUID messageId,
        AiChatReferenceType referenceType,
        UUID referenceId,
        String label,
        Instant createdAt
) {
    /** AiChatContextReference 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AiChatContextReference {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(referenceType, "referenceType must not be null");
        Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /** AiChatContextReference의 create 처리에 필요한 데이터를 생성하거나 저장한다. */
    public static AiChatContextReference create(UUID messageId, AiChatReferenceType referenceType, UUID referenceId, String label) {
        return new AiChatContextReference(UUID.randomUUID(), messageId, referenceType, referenceId, label, Instant.now());
    }
}
