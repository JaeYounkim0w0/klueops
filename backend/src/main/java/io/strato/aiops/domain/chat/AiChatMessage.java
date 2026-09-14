package io.strato.aiops.domain.chat;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AiChatMessage {

    private final UUID id;
    private final UUID conversationId;
    private final AiChatRole role;
    private final String content;
    private final String model;
    private final String promptVersion;
    private final String finishReason;
    private final Long latencyMs;
    private final Long firstTokenLatencyMs;
    private final Long totalLatencyMs;
    private final Integer contextChars;
    private final String errorCode;
    private final String errorMessage;
    private final String createdBy;
    private final Instant createdAt;

    public AiChatMessage(UUID id, UUID conversationId, AiChatRole role, String content, String model,
                         String promptVersion, String finishReason, Long latencyMs, Long firstTokenLatencyMs,
                         Long totalLatencyMs, Integer contextChars, String errorCode,
                         String errorMessage, String createdBy, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.conversationId = Objects.requireNonNull(conversationId, "conversationId must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.model = model;
        this.promptVersion = promptVersion;
        this.finishReason = finishReason;
        this.latencyMs = latencyMs;
        this.firstTokenLatencyMs = firstTokenLatencyMs;
        this.totalLatencyMs = totalLatencyMs;
        this.contextChars = contextChars;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static AiChatMessage user(UUID conversationId, String content, String actor) {
        return new AiChatMessage(UUID.randomUUID(), conversationId, AiChatRole.USER, content, null, null,
                null, null, null, null, null, null, null, actor, Instant.now());
    }

    public static AiChatMessage assistant(UUID conversationId, String content, String model, String promptVersion,
                                          String finishReason, long latencyMs, String actor) {
        return assistant(conversationId, content, model, promptVersion, finishReason, latencyMs,
                latencyMs, latencyMs, null, actor);
    }

    public static AiChatMessage assistant(UUID conversationId, String content, String model, String promptVersion,
                                          String finishReason, long latencyMs, long firstTokenLatencyMs,
                                          long totalLatencyMs, Integer contextChars, String actor) {
        return new AiChatMessage(UUID.randomUUID(), conversationId, AiChatRole.ASSISTANT, content, model,
                promptVersion, finishReason, latencyMs, firstTokenLatencyMs, totalLatencyMs, contextChars,
                null, null, actor, Instant.now());
    }

    public static AiChatMessage interruptedAssistant(UUID conversationId, String partialContent, String promptVersion,
                                                     long totalLatencyMs, int contextChars) {
        return new AiChatMessage(UUID.randomUUID(), conversationId, AiChatRole.ASSISTANT,
                partialContent == null ? "" : partialContent, null, promptVersion, "ERROR", null,
                null, totalLatencyMs, contextChars, "STREAM_INTERRUPTED",
                "AI response stream was interrupted", "ollama", Instant.now());
    }

    public UUID id() { return id; }
    public UUID conversationId() { return conversationId; }
    public AiChatRole role() { return role; }
    public String content() { return content; }
    public String model() { return model; }
    public String promptVersion() { return promptVersion; }
    public String finishReason() { return finishReason; }
    public Long latencyMs() { return latencyMs; }
    public Long firstTokenLatencyMs() { return firstTokenLatencyMs; }
    public Long totalLatencyMs() { return totalLatencyMs; }
    public Integer contextChars() { return contextChars; }
    public String errorCode() { return errorCode; }
    public String errorMessage() { return errorMessage; }
    public String createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }
}
