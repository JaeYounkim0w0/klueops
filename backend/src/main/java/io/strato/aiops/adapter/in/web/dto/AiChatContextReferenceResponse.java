package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.domain.chat.AiChatContextReference;
import io.strato.aiops.domain.chat.AiChatReferenceType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "AI chat context reference")
public record AiChatContextReferenceResponse(
        UUID id,
        UUID messageId,
        AiChatReferenceType referenceType,
        UUID referenceId,
        String label,
        Instant createdAt
) {
    public static AiChatContextReferenceResponse from(AiChatContextReference reference) {
        return new AiChatContextReferenceResponse(reference.id(), reference.messageId(), reference.referenceType(),
                reference.referenceId(), reference.label(), reference.createdAt());
    }
}
