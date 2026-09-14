package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.chat.AiChatContextReference;
import io.strato.aiops.domain.chat.AiChatConversation;
import io.strato.aiops.domain.chat.AiChatMessage;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public interface AiChatUseCase {

    AiChatConversation createConversation(CreateAiChatConversationCommand command, String actor, String requestId);

    List<AiChatConversation> listConversations(String actor, boolean archived);

    AiChatConversation getConversation(UUID conversationId, String actor);

    List<AiChatMessage> listMessages(UUID conversationId, String actor);

    List<AiChatContextReference> listContextReferences(UUID messageId, String actor);

    AiChatConversation getConversationByMessage(UUID messageId, String actor);

    List<AiChatContextReference> listConversationContextReferences(UUID conversationId, String actor);

    AiChatConversation updateConversation(UUID conversationId, String title, Boolean favorite, Boolean archived,
                                          String actor, String requestId);

    void deleteConversation(UUID conversationId, String actor, String requestId);

    AiChatSendMessageResult sendMessage(SendAiChatMessageCommand command, String actor, String requestId);

    AiChatSendMessageResult streamMessage(SendAiChatMessageCommand command, String actor, String requestId,
                                          Consumer<String> onDelta);
}
