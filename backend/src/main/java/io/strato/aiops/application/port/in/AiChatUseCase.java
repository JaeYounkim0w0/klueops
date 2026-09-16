package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.chat.AiChatContextReference;
import io.strato.aiops.domain.chat.AiChatConversation;
import io.strato.aiops.domain.chat.AiChatMessage;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public interface AiChatUseCase {

    /** AiChatUseCase의 createConversation 처리에 필요한 데이터를 생성하거나 저장한다. */
    AiChatConversation createConversation(CreateAiChatConversationCommand command, String actor, String requestId);

    /** AiChatUseCase의 listConversations 처리 결과를 조회해 반환한다. */
    List<AiChatConversation> listConversations(String actor, boolean archived);

    /** AiChatUseCase의 getConversation 처리 결과를 조회해 반환한다. */
    AiChatConversation getConversation(UUID conversationId, String actor);

    /** AiChatUseCase의 listMessages 처리 결과를 조회해 반환한다. */
    List<AiChatMessage> listMessages(UUID conversationId, String actor);

    /** AiChatUseCase의 listContextReferences 처리 결과를 조회해 반환한다. */
    List<AiChatContextReference> listContextReferences(UUID messageId, String actor);

    /** AiChatUseCase의 getConversationByMessage 처리 결과를 조회해 반환한다. */
    AiChatConversation getConversationByMessage(UUID messageId, String actor);

    /** AiChatUseCase의 listConversationContextReferences 처리 결과를 조회해 반환한다. */
    List<AiChatContextReference> listConversationContextReferences(UUID conversationId, String actor);

    /** AiChatUseCase의 updateConversation 처리 대상의 상태를 갱신한다. */
    AiChatConversation updateConversation(UUID conversationId, String title, Boolean favorite, Boolean archived,
                                          String actor, String requestId);

    /** AiChatUseCase의 deleteConversation 처리 대상과 관련 상태를 안전하게 정리한다. */
    void deleteConversation(UUID conversationId, String actor, String requestId);

    /** AiChatUseCase의 sendMessage 처리 결과를 지정된 대상에 전달한다. */
    AiChatSendMessageResult sendMessage(SendAiChatMessageCommand command, String actor, String requestId);

    /** AiChatUseCase의 streamMessage 처리 계약을 정의한다. */
    AiChatSendMessageResult streamMessage(SendAiChatMessageCommand command, String actor, String requestId,
                                          Consumer<String> onDelta);
}
