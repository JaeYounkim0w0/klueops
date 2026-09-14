package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.AiChatSendMessageResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "AI chat message send result")
public record AiChatSendMessageResponse(
        UUID conversationId,
        AiChatMessageResponse userMessage,
        AiChatMessageResponse assistantMessage,
        List<AiChatContextReferenceResponse> contextReferences
) {
    public static AiChatSendMessageResponse from(AiChatSendMessageResult result) {
        return new AiChatSendMessageResponse(result.conversationId(),
                AiChatMessageResponse.from(result.userMessage()),
                AiChatMessageResponse.from(result.assistantMessage()),
                result.contextReferences().stream().map(AiChatContextReferenceResponse::from).toList());
    }
}
