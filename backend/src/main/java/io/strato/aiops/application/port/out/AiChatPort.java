package io.strato.aiops.application.port.out;

import java.util.function.Consumer;

public interface AiChatPort {

    AiChatCompletion complete(AiChatPrompt prompt);

    default AiChatCompletion stream(AiChatPrompt prompt, Consumer<String> onDelta) {
        AiChatCompletion completion = complete(prompt);
        onDelta.accept(completion.content());
        return completion;
    }
}
