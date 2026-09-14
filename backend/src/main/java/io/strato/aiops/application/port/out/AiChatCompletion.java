package io.strato.aiops.application.port.out;

public record AiChatCompletion(
        String content,
        String model,
        String finishReason,
        long latencyMs,
        long firstTokenLatencyMs
) {
}
