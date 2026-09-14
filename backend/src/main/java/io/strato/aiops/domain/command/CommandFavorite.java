package io.strato.aiops.domain.command;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CommandFavorite(
        UUID id,
        UUID clusterId,
        String ownerUserId,
        String name,
        String description,
        String command,
        String namespace,
        boolean shared,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt
) {
    public CommandFavorite {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(clusterId, "clusterId must not be null");
        Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static CommandFavorite create(UUID clusterId, String owner, String name, String description,
                                         String command, String namespace, boolean shared, int sortOrder, Instant now) {
        return new CommandFavorite(UUID.randomUUID(), clusterId, owner, name, description, command, namespace,
                shared, sortOrder, now, now);
    }

    public CommandFavorite update(String newName, String newDescription, String newCommand, String newNamespace,
                                  boolean newShared, int newSortOrder, Instant now) {
        return new CommandFavorite(id, clusterId, ownerUserId, newName, newDescription, newCommand, newNamespace,
                newShared, newSortOrder, createdAt, now);
    }
}
