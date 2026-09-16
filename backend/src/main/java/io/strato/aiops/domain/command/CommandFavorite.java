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
    /** CommandFavorite 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public CommandFavorite {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(clusterId, "clusterId must not be null");
        Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /** CommandFavorite의 create 처리에 필요한 데이터를 생성하거나 저장한다. */
    public static CommandFavorite create(UUID clusterId, String owner, String name, String description,
                                         String command, String namespace, boolean shared, int sortOrder, Instant now) {
        return new CommandFavorite(UUID.randomUUID(), clusterId, owner, name, description, command, namespace,
                shared, sortOrder, now, now);
    }

    /** CommandFavorite의 update 처리 대상의 상태를 갱신한다. */
    public CommandFavorite update(String newName, String newDescription, String newCommand, String newNamespace,
                                  boolean newShared, int newSortOrder, Instant now) {
        return new CommandFavorite(id, clusterId, ownerUserId, newName, newDescription, newCommand, newNamespace,
                newShared, newSortOrder, createdAt, now);
    }
}
