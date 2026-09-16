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
    /** AiChatSendMessageResponse의 from 처리 데이터를 필요한 표현으로 변환한다. */
    public static AiChatSendMessageResponse from(AiChatSendMessageResult result) {
        return new AiChatSendMessageResponse(result.conversationId(),
                AiChatMessageResponse.from(result.userMessage()),
                AiChatMessageResponse.from(result.assistantMessage()),
                result.contextReferences().stream().map(AiChatContextReferenceResponse::from).toList());
    }
}
