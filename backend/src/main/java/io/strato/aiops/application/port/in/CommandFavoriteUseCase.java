package io.strato.aiops.application.port.in;

import io.strato.aiops.domain.command.CommandFavorite;

import java.util.List;
import java.util.UUID;

public interface CommandFavoriteUseCase {
    List<CommandFavorite> list(UUID clusterId, String actor);
    CommandFavorite create(UUID clusterId, String name, String description, String command, String namespace,
                           boolean shared, int sortOrder, String actor);
    CommandFavorite update(UUID clusterId, UUID favoriteId, String name, String description, String command,
                           String namespace, boolean shared, int sortOrder, String actor);
    void delete(UUID clusterId, UUID favoriteId, String actor);
}

