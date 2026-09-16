package io.strato.aiops.application.port.out;

import java.util.function.Consumer;

public interface AiChatPort {

    /** AiChatPort의 complete 처리 계약을 정의한다. */
    AiChatCompletion complete(AiChatPrompt prompt);

    /** AiChatPort의 stream 처리에 필요한 업무 로직을 수행한다. */
    default AiChatCompletion stream(AiChatPrompt prompt, Consumer<String> onDelta) {
        AiChatCompletion completion = complete(prompt);
        onDelta.accept(completion.content());
        return completion;
    }
}
