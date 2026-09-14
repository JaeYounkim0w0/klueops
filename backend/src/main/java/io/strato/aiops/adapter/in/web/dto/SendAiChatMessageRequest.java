package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.SendAiChatMessageCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "AI chat message request")
public record SendAiChatMessageRequest(
        @NotBlank @Size(max = 4000) String message,
        @Valid AiChatContextSelectionRequest contextSelection
) {
    public SendAiChatMessageCommand toCommand(UUID conversationId) {
        return new SendAiChatMessageCommand(conversationId, message,
                contextSelection == null ? null : contextSelection.toCommand());
    }
}
