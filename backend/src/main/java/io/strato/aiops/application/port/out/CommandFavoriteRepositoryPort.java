package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.command.CommandFavorite;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommandFavoriteRepositoryPort {
    CommandFavorite save(CommandFavorite favorite);
    Optional<CommandFavorite> findById(UUID id);
    List<CommandFavorite> findVisible(UUID clusterId, String owner);
    void deleteById(UUID id);
}

