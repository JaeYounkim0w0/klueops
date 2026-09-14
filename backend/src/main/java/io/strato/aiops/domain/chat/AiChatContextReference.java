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
    public AiChatContextReference {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(referenceType, "referenceType must not be null");
        Objects.requireNonNull(label, "label must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static AiChatContextReference create(UUID messageId, AiChatReferenceType referenceType, UUID referenceId, String label) {
        return new AiChatContextReference(UUID.randomUUID(), messageId, referenceType, referenceId, label, Instant.now());
    }
}
