package io.strato.aiops.adapter.out.persistence;

import io.strato.aiops.application.port.out.CommandFavoriteRepositoryPort;
import io.strato.aiops.domain.command.CommandFavorite;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaCommandFavoriteRepositoryAdapter implements CommandFavoriteRepositoryPort {
    private final CommandFavoriteJpaRepository repository;

    public JpaCommandFavoriteRepositoryAdapter(CommandFavoriteJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public CommandFavorite save(CommandFavorite favorite) {
        return repository.save(CommandFavoriteEntity.fromDomain(favorite)).toDomain();
    }

    @Override
    public Optional<CommandFavorite> findById(UUID id) {
        return repository.findById(id).map(CommandFavoriteEntity::toDomain);
    }

    @Override
    public List<CommandFavorite> findVisible(UUID clusterId, String owner) {
        return repository.findByClusterIdAndOwnerUserIdOrClusterIdAndSharedTrueOrderBySortOrderAscUpdatedAtDesc(
                        clusterId, owner, clusterId).stream()
                .map(CommandFavoriteEntity::toDomain)
                .distinct()
                .toList();
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }
}
