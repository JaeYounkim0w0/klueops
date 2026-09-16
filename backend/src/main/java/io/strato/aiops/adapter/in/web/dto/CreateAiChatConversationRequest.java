package io.strato.aiops.adapter.in.web.dto;

import io.strato.aiops.application.port.in.CreateAiChatConversationCommand;
import io.strato.aiops.domain.chat.AiChatMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "AI chat conversation creation request")
public record CreateAiChatConversationRequest(
        @Size(max = 255) String title,
        AiChatMode mode,
        UUID clusterId,
        @Size(max = 253) String namespace,
        UUID applicationId
) {
    /** CreateAiChatConversationRequest의 toCommand 처리 데이터를 필요한 표현으로 변환한다. */
    public CreateAiChatConversationCommand toCommand() {
        AiChatMode resolvedMode = mode == null
                ? (clusterId == null && applicationId == null ? AiChatMode.GENERAL : AiChatMode.CLUSTER)
                : mode;
        return new CreateAiChatConversationCommand(title, resolvedMode, clusterId, namespace, applicationId);
    }
}
