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

    /** RoutedAiChatAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public RoutedAiChatAdapter(@Qualifier("ollamaAiChatAdapter") AiChatPort localFallback,
                               ConfiguredAiClient configured) {
        this.localFallback = localFallback;
        this.configured = configured;
    }

    /** RoutedAiChatAdapter의 complete 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public AiChatCompletion complete(AiChatPrompt prompt) {
        return configured.complete(prompt.tenantId(), "CHAT", system(prompt), prompt.userMessage())
                .map(this::completion)
                .orElseGet(() -> localFallback.complete(prompt));
    }

    /** RoutedAiChatAdapter의 stream 처리에 필요한 업무 로직을 수행한다. */
    @Override
    public AiChatCompletion stream(AiChatPrompt prompt, Consumer<String> onDelta) {
        var selected = configured.complete(prompt.tenantId(), "CHAT", system(prompt), prompt.userMessage());
        if (selected.isEmpty()) return localFallback.stream(prompt, onDelta);
        // Provider 공통 경로는 우선 완결 응답을 사용하되 SSE 계약은 동일하게 유지한다.
        onDelta.accept(selected.get().content());
        return completion(selected.get());
    }

    /** RoutedAiChatAdapter의 completion 처리에 필요한 업무 로직을 수행한다. */
    private AiChatCompletion completion(ConfiguredAiClient.Completion value) {
        return new AiChatCompletion(value.content(), value.model(), "STOP", value.latencyMs(), value.latencyMs());
    }

    /** RoutedAiChatAdapter의 system 처리에 필요한 업무 로직을 수행한다. */
    private String system(AiChatPrompt prompt) {
        return prompt.sanitizedContext() != null && prompt.sanitizedContext().startsWith("mode=GENERAL")
                ? "You are a helpful general-purpose assistant. Do not claim access to external systems."
                : "You are the KlueOps Kubernetes operations assistant. Use only this sanitized context:\n"
                + (prompt.sanitizedContext() == null ? "" : prompt.sanitizedContext());
    }
}
