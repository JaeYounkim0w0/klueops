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

    /** AiChatMessage 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
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

    /** AiChatMessage의 user 처리에 필요한 업무 로직을 수행한다. */
    public static AiChatMessage user(UUID conversationId, String content, String actor) {
        return new AiChatMessage(UUID.randomUUID(), conversationId, AiChatRole.USER, content, null, null,
                null, null, null, null, null, null, null, actor, Instant.now());
    }

    /** AiChatMessage의 assistant 처리에 필요한 업무 로직을 수행한다. */
    public static AiChatMessage assistant(UUID conversationId, String content, String model, String promptVersion,
                                          String finishReason, long latencyMs, String actor) {
        return assistant(conversationId, content, model, promptVersion, finishReason, latencyMs,
                latencyMs, latencyMs, null, actor);
    }

    /** AiChatMessage의 assistant 처리에 필요한 업무 로직을 수행한다. */
    public static AiChatMessage assistant(UUID conversationId, String content, String model, String promptVersion,
                                          String finishReason, long latencyMs, long firstTokenLatencyMs,
                                          long totalLatencyMs, Integer contextChars, String actor) {
        return new AiChatMessage(UUID.randomUUID(), conversationId, AiChatRole.ASSISTANT, content, model,
                promptVersion, finishReason, latencyMs, firstTokenLatencyMs, totalLatencyMs, contextChars,
                null, null, actor, Instant.now());
    }

    /** AiChatMessage의 interruptedAssistant 처리에 필요한 업무 로직을 수행한다. */
    public static AiChatMessage interruptedAssistant(UUID conversationId, String partialContent, String promptVersion,
                                                     long totalLatencyMs, int contextChars) {
        return new AiChatMessage(UUID.randomUUID(), conversationId, AiChatRole.ASSISTANT,
                partialContent == null ? "" : partialContent, null, promptVersion, "ERROR", null,
                null, totalLatencyMs, contextChars, "STREAM_INTERRUPTED",
                "AI response stream was interrupted", "ollama", Instant.now());
    }

    /** AiChatMessage의 id 처리에 필요한 업무 로직을 수행한다. */
    public UUID id() { return id; }
    /** AiChatMessage의 conversationId 처리에 필요한 업무 로직을 수행한다. */
    public UUID conversationId() { return conversationId; }
    /** AiChatMessage의 role 처리에 필요한 업무 로직을 수행한다. */
    public AiChatRole role() { return role; }
    /** AiChatMessage의 content 처리에 필요한 업무 로직을 수행한다. */
    public String content() { return content; }
    /** AiChatMessage의 model 처리에 필요한 업무 로직을 수행한다. */
    public String model() { return model; }
    /** AiChatMessage의 promptVersion 처리에 필요한 업무 로직을 수행한다. */
    public String promptVersion() { return promptVersion; }
    /** AiChatMessage의 finishReason 처리에 필요한 업무 로직을 수행한다. */
    public String finishReason() { return finishReason; }
    /** AiChatMessage의 latencyMs 처리에 필요한 업무 로직을 수행한다. */
    public Long latencyMs() { return latencyMs; }
    /** AiChatMessage의 firstTokenLatencyMs 처리에 필요한 업무 로직을 수행한다. */
    public Long firstTokenLatencyMs() { return firstTokenLatencyMs; }
    /** AiChatMessage의 totalLatencyMs 처리 데이터를 필요한 표현으로 변환한다. */
    public Long totalLatencyMs() { return totalLatencyMs; }
    /** AiChatMessage의 contextChars 처리에 필요한 업무 로직을 수행한다. */
    public Integer contextChars() { return contextChars; }
    /** AiChatMessage의 errorCode 처리에 필요한 업무 로직을 수행한다. */
    public String errorCode() { return errorCode; }
    /** AiChatMessage의 errorMessage 처리에 필요한 업무 로직을 수행한다. */
    public String errorMessage() { return errorMessage; }
    /** AiChatMessage의 createdBy 처리에 필요한 데이터를 생성하거나 저장한다. */
    public String createdBy() { return createdBy; }
    /** AiChatMessage의 createdAt 처리에 필요한 데이터를 생성하거나 저장한다. */
    public Instant createdAt() { return createdAt; }
}
