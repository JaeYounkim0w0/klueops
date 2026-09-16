package io.strato.aiops.domain.chat;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AiChatConversation {

    private final UUID id;
    private final String title;
    private final AiChatMode mode;
    private final boolean favorite;
    private final UUID clusterId;
    private final String namespace;
    private final UUID applicationId;
    private final String createdBy;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant archivedAt;

    /** AiChatConversation 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public AiChatConversation(UUID id, String title, AiChatMode mode, boolean favorite, UUID clusterId, String namespace, UUID applicationId,
                              String createdBy, Instant createdAt, Instant updatedAt, Instant archivedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
        this.favorite = favorite;
        this.clusterId = clusterId;
        this.namespace = namespace;
        this.applicationId = applicationId;
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.archivedAt = archivedAt;
    }

    /** AiChatConversation의 create 처리에 필요한 데이터를 생성하거나 저장한다. */
    public static AiChatConversation create(String title, AiChatMode mode, UUID clusterId, String namespace, UUID applicationId, String actor) {
        Instant now = Instant.now();
        return new AiChatConversation(UUID.randomUUID(), title, mode, false, clusterId, namespace, applicationId, actor, now, now, null);
    }

    /** AiChatConversation의 update 처리 대상의 상태를 갱신한다. */
    public AiChatConversation update(String title, Boolean favorite, Boolean archived) {
        String nextTitle = title == null ? this.title : title.trim();
        if (nextTitle.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        Instant now = Instant.now();
        Instant nextArchivedAt = archived == null ? archivedAt : archived ? now : null;
        return new AiChatConversation(id, nextTitle, mode, favorite == null ? this.favorite : favorite,
                clusterId, namespace, applicationId, createdBy, createdAt, now, nextArchivedAt);
    }

    /** AiChatConversation의 id 처리에 필요한 업무 로직을 수행한다. */
    public UUID id() { return id; }
    /** AiChatConversation의 title 처리에 필요한 업무 로직을 수행한다. */
    public String title() { return title; }
    /** AiChatConversation의 mode 처리에 필요한 업무 로직을 수행한다. */
    public AiChatMode mode() { return mode; }
    /** AiChatConversation의 favorite 처리에 필요한 업무 로직을 수행한다. */
    public boolean favorite() { return favorite; }
    /** AiChatConversation의 clusterId 처리에 필요한 업무 로직을 수행한다. */
    public UUID clusterId() { return clusterId; }
    /** AiChatConversation의 namespace 처리에 필요한 업무 로직을 수행한다. */
    public String namespace() { return namespace; }
    /** AiChatConversation의 applicationId 처리에 필요한 업무 로직을 수행한다. */
    public UUID applicationId() { return applicationId; }
    /** AiChatConversation의 createdBy 처리에 필요한 데이터를 생성하거나 저장한다. */
    public String createdBy() { return createdBy; }
    /** AiChatConversation의 createdAt 처리에 필요한 데이터를 생성하거나 저장한다. */
    public Instant createdAt() { return createdAt; }
    /** AiChatConversation의 updatedAt 처리 대상의 상태를 갱신한다. */
    public Instant updatedAt() { return updatedAt; }
    /** AiChatConversation의 archivedAt 처리에 필요한 업무 로직을 수행한다. */
    public Instant archivedAt() { return archivedAt; }
}
