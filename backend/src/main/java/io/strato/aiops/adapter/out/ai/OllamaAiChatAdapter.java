package io.strato.aiops.adapter.out.ai;

import io.strato.aiops.application.port.out.AiChatCompletion;
import io.strato.aiops.application.port.out.AiChatPort;
import io.strato.aiops.application.port.out.AiChatPrompt;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Fallback;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Component
@Fallback
public class OllamaAiChatAdapter implements AiChatPort {

    private final ChatClient chatClient;
    private final String model;
    private final double temperature;

    /** OllamaAiChatAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public OllamaAiChatAdapter(ChatClient.Builder chatClientBuilder,
                               AiChatMessageBackedChatMemoryRepository chatMemoryRepository,
                               @Value("${aiops.ai.model:qwen2.5-coder:7b}") String model,
                               @Value("${aiops.ai.temperature:0.2}") double temperature,
                               @Value("${aiops.ai.chat-memory.max-messages:20}") int maxMemoryMessages) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(maxMemoryMessages)
                .build();
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.model = model;
        this.temperature = temperature;
    }

    /** OllamaAiChatAdapter의 complete 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public AiChatCompletion complete(AiChatPrompt prompt) {
        String systemPrompt = systemPrompt(prompt);
        String context = prompt.sanitizedContext() == null ? "" : prompt.sanitizedContext();
        String userMessage = prompt.userMessage() == null ? "" : prompt.userMessage();
        long startedAt = System.nanoTime();
        try {
            ChatResponse response = chatClient.prompt()
                    .system(systemPrompt + "\n\nContext:\n" + context)
                    .user(userMessage)
                    .options(OllamaChatOptions.builder()
                            .model(model)
                            .temperature(temperature)
                            .build())
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, prompt.conversationId().toString()))
                    .call()
                    .chatResponse();
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null
                    || response.getResult().getOutput().getText() == null
                    || response.getResult().getOutput().getText().isBlank()) {
                throw new OllamaAiException("Ollama response was empty");
            }
            long latencyMs = Math.max(1, (System.nanoTime() - startedAt) / 1_000_000);
            String responseModel = response.getMetadata() == null || response.getMetadata().getModel() == null
                    ? model
                    : response.getMetadata().getModel();
            String finishReason = response.getResult().getMetadata() == null
                    ? "UNKNOWN"
                    : response.getResult().getMetadata().getFinishReason();
            return new AiChatCompletion(response.getResult().getOutput().getText(), responseModel,
                    finishReason == null || finishReason.isBlank() ? "UNKNOWN" : finishReason.toUpperCase(Locale.ROOT),
                    latencyMs, latencyMs);
        } catch (OllamaAiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OllamaAiException("Ollama request failed: " + exception.getMessage(), exception);
        }
    }

    /** OllamaAiChatAdapter의 stream 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public AiChatCompletion stream(AiChatPrompt prompt, Consumer<String> onDelta) {
        String context = prompt.sanitizedContext() == null ? "" : prompt.sanitizedContext();
        String userMessage = prompt.userMessage() == null ? "" : prompt.userMessage();
        StringBuilder content = new StringBuilder();
        AtomicLong firstTokenLatencyMs = new AtomicLong();
        long startedAt = System.nanoTime();
        try {
            chatClient.prompt()
                    .system(systemPrompt(prompt) + "\n\nContext:\n" + context)
                    .user(userMessage)
                    .options(OllamaChatOptions.builder().model(model).temperature(temperature).build())
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, prompt.conversationId().toString()))
                    .stream()
                    .content()
                    .doOnNext(delta -> {
                        firstTokenLatencyMs.compareAndSet(0,
                                Math.max(1, (System.nanoTime() - startedAt) / 1_000_000));
                        content.append(delta);
                        onDelta.accept(delta);
                    })
                    .blockLast();
            if (content.isEmpty()) {
                throw new OllamaAiException("Ollama response was empty");
            }
            return new AiChatCompletion(content.toString(), model, "STOP",
                    Math.max(1, (System.nanoTime() - startedAt) / 1_000_000), firstTokenLatencyMs.get());
        } catch (OllamaAiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OllamaAiException("Ollama streaming request failed: " + exception.getMessage(), exception);
        }
    }

    /** OllamaAiChatAdapter의 systemPrompt 처리에 필요한 업무 로직을 수행한다. */
    private String systemPrompt(AiChatPrompt prompt) {
        if (prompt.sanitizedContext() != null && prompt.sanitizedContext().startsWith("mode=GENERAL")) {
            return """
                    You are a helpful general-purpose AI assistant.
                    Do not claim access to Kubernetes or external systems.
                    """;
        }
        return """
                You are the KlueOps AI Chat Assistant for evidence-guided Kubernetes operations.
                You answer Kubernetes operations questions using only the provided sanitized context.
                Never claim that you executed kubectl, helm, or any cluster mutation.
                Clearly mark recommendations as recommendations.
                """;
    }
}
