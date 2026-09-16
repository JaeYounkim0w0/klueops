package io.strato.aiops.adapter.out.ai;

import io.strato.aiops.application.port.out.AiChatMessageRepositoryPort;
import io.strato.aiops.domain.chat.AiChatMessage;
import io.strato.aiops.domain.chat.AiChatRole;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class AiChatMessageBackedChatMemoryRepository implements ChatMemoryRepository {

    private final AiChatMessageRepositoryPort messageRepositoryPort;
    private final int maxMessages;

    /** AiChatMessageBackedChatMemoryRepository 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AiChatMessageBackedChatMemoryRepository(AiChatMessageRepositoryPort messageRepositoryPort,
                                                   @Value("${aiops.ai.chat-memory.max-messages:20}") int maxMessages) {
        this.messageRepositoryPort = messageRepositoryPort;
        this.maxMessages = maxMessages;
    }

    /** AiChatMessageBackedChatMemoryRepository의 findConversationIds 처리 결과를 조회해 반환한다. */
    @Override
    public List<String> findConversationIds() {
        return List.of();
    }

    /** AiChatMessageBackedChatMemoryRepository의 findByConversationId 처리 결과를 조회해 반환한다. */
    @Override
    public List<Message> findByConversationId(String conversationId) {
        UUID id = parseConversationId(conversationId);
        if (id == null) {
            return List.of();
        }
        return messageRepositoryPort.findByConversationId(id, maxMessages).stream()
                .map(this::toSpringAiMessage)
                .flatMap(List::stream)
                .toList();
    }

    /** AiChatMessageBackedChatMemoryRepository의 saveAll 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        // Domain service persists the complete chat history. Spring AI memory reads from that history
        // and should not write duplicate messages through the advisor lifecycle.
    }

    /** AiChatMessageBackedChatMemoryRepository의 deleteByConversationId 처리 대상과 관련 상태를 안전하게 정리한다. */
    @Override
    public void deleteByConversationId(String conversationId) {
        // Conversation deletion is owned by the application domain, not the memory advisor.
    }

    /** AiChatMessageBackedChatMemoryRepository의 parseConversationId 처리 데이터를 필요한 표현으로 변환한다. */
    private UUID parseConversationId(String conversationId) {
        try {
            return UUID.fromString(conversationId);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** AiChatMessageBackedChatMemoryRepository의 toSpringAiMessage 처리 데이터를 필요한 표현으로 변환한다. */
    private List<Message> toSpringAiMessage(AiChatMessage message) {
        if (message.errorCode() != null) {
            return List.of();
        }
        if (message.role() == AiChatRole.USER) {
            return List.of(new UserMessage(message.content()));
        }
        if (message.role() == AiChatRole.ASSISTANT) {
            return List.of(new AssistantMessage(message.content()));
        }
        return List.of();
    }
}
