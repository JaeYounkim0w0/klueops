package io.strato.aiops.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "AI chat conversation partial update")
public record UpdateAiChatConversationRequest(
        @Size(max = 255) String title,
        Boolean favorite,
        Boolean archived
) {
}
