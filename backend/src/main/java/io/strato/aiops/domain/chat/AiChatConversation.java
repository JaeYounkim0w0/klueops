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

    public static AiChatConversation create(String title, AiChatMode mode, UUID clusterId, String namespace, UUID applicationId, String actor) {
        Instant now = Instant.now();
        return new AiChatConversation(UUID.randomUUID(), title, mode, false, clusterId, namespace, applicationId, actor, now, now, null);
    }

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

    public UUID id() { return id; }
    public String title() { return title; }
    public AiChatMode mode() { return mode; }
    public boolean favorite() { return favorite; }
    public UUID clusterId() { return clusterId; }
    public String namespace() { return namespace; }
    public UUID applicationId() { return applicationId; }
    public String createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant archivedAt() { return archivedAt; }
}
