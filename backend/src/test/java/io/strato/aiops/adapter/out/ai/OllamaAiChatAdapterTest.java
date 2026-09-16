package io.strato.aiops.adapter.out.ai;

import io.strato.aiops.application.port.out.AiChatCompletion;
import io.strato.aiops.application.port.out.AiChatMessageRepositoryPort;
import io.strato.aiops.application.port.out.AiChatPrompt;
import io.strato.aiops.domain.chat.AiChatMessage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaAiChatAdapterTest {

    /** OllamaAiChatAdapterTest의 includesPersistedConversationMemoryWhenCallingChatClient 처리에 필요한 업무 로직을 수행한다. */
    @Test
    void includesPersistedConversationMemoryWhenCallingChatClient() {
        UUID conversationId = UUID.randomUUID();
        FakeMessageRepository messageRepository = new FakeMessageRepository(List.of(
                AiChatMessage.user(conversationId, "이전 질문", "tester"),
                AiChatMessage.assistant(conversationId, "이전 답변", "test-model", "ai-chat.v1", "STOP", 1, "ollama")
        ));
        CapturingChatModel chatModel = new CapturingChatModel();
        OllamaAiChatAdapter adapter = new OllamaAiChatAdapter(
                ChatClient.builder(chatModel),
                new AiChatMessageBackedChatMemoryRepository(messageRepository, 20),
                "test-model",
                0.2,
                20
        );

        AiChatCompletion completion = adapter.complete(new AiChatPrompt(
                conversationId,
                "현재 질문",
                "namespace=default",
                "ai-chat.v1"
        ));

        List<String> promptTexts = chatModel.prompt.getInstructions().stream()
                .map(Message::getText)
                .toList();
        assertThat(promptTexts).contains("이전 질문", "이전 답변");
        assertThat(promptTexts.get(promptTexts.size() - 1)).isEqualTo("현재 질문");
        assertThat(completion.content()).isEqualTo("memory-aware response");
        assertThat(completion.model()).isEqualTo("test-model");
        assertThat(completion.finishReason()).isEqualTo("STOP");
        assertThat(completion.firstTokenLatencyMs()).isPositive();
    }

    private static final class CapturingChatModel implements ChatModel {

        private Prompt prompt;

        /** CapturingChatModel의 call 처리에 필요한 업무 로직을 수행한다. */
        @Override
        public ChatResponse call(Prompt prompt) {
            this.prompt = prompt;
            return new ChatResponse(List.of(new Generation(
                    new AssistantMessage("memory-aware response"),
                    ChatGenerationMetadata.builder().finishReason("stop").build()
            )), ChatResponseMetadata.builder().model("test-model").build());
        }
    }

    private static final class FakeMessageRepository implements AiChatMessageRepositoryPort {

        private final List<AiChatMessage> messages;

        /** FakeMessageRepository 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
        private FakeMessageRepository(List<AiChatMessage> messages) {
            this.messages = new ArrayList<>(messages);
        }

        /** FakeMessageRepository의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
        @Override
        public AiChatMessage save(AiChatMessage message) {
            messages.add(message);
            return message;
        }

        /** FakeMessageRepository의 findByConversationId 처리 결과를 조회해 반환한다. */
        @Override
        public List<AiChatMessage> findByConversationId(UUID conversationId, int limit) {
            return messages.stream()
                    .filter(message -> message.conversationId().equals(conversationId))
                    .limit(limit)
                    .toList();
        }
    }
}
