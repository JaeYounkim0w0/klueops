package io.strato.aiops.adapter.out.ai;

import io.strato.aiops.application.port.out.AiChatCompletion;
import io.strato.aiops.application.port.out.AiChatPort;
import io.strato.aiops.application.port.out.AiChatPrompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
public class RoutedAiChatAdapter implements AiChatPort {
    private final AiChatPort localFallback;
    private final ConfiguredAiClient configured;

    public RoutedAiChatAdapter(@Qualifier("ollamaAiChatAdapter") AiChatPort localFallback,
                               ConfiguredAiClient configured) {
        this.localFallback = localFallback;
        this.configured = configured;
    }

    @Override
    public AiChatCompletion complete(AiChatPrompt prompt) {
        return configured.complete(prompt.tenantId(), "CHAT", system(prompt), prompt.userMessage())
                .map(this::completion)
                .orElseGet(() -> localFallback.complete(prompt));
    }

    @Override
    public AiChatCompletion stream(AiChatPrompt prompt, Consumer<String> onDelta) {
        var selected = configured.complete(prompt.tenantId(), "CHAT", system(prompt), prompt.userMessage());
        if (selected.isEmpty()) return localFallback.stream(prompt, onDelta);
        // Provider 공통 경로는 우선 완결 응답을 사용하되 SSE 계약은 동일하게 유지한다.
        onDelta.accept(selected.get().content());
        return completion(selected.get());
    }

    private AiChatCompletion completion(ConfiguredAiClient.Completion value) {
        return new AiChatCompletion(value.content(), value.model(), "STOP", value.latencyMs(), value.latencyMs());
    }

    private String system(AiChatPrompt prompt) {
        return prompt.sanitizedContext() != null && prompt.sanitizedContext().startsWith("mode=GENERAL")
                ? "You are a helpful general-purpose assistant. Do not claim access to external systems."
                : "You are the KlueOps Kubernetes operations assistant. Use only this sanitized context:\n"
                + (prompt.sanitizedContext() == null ? "" : prompt.sanitizedContext());
    }
}
