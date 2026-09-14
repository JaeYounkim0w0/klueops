package io.strato.aiops.application.service;

import io.strato.aiops.application.port.in.CommandFavoriteUseCase;
import io.strato.aiops.application.port.out.ClusterRepositoryPort;
import io.strato.aiops.application.port.out.CommandFavoriteRepositoryPort;
import io.strato.aiops.domain.command.CommandFavorite;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class CommandFavoriteApplicationService implements CommandFavoriteUseCase {
    private final CommandFavoriteRepositoryPort repository;
    private final ClusterRepositoryPort clusterRepository;
    private final KubectlCommandTokenizer tokenizer;
    private final Clock clock;

    public CommandFavoriteApplicationService(CommandFavoriteRepositoryPort repository,
                                             ClusterRepositoryPort clusterRepository,
                                             KubectlCommandTokenizer tokenizer,
                                             Clock clock) {
        this.repository = repository;
        this.clusterRepository = clusterRepository;
        this.tokenizer = tokenizer;
        this.clock = clock;
    }

    @Override
    public List<CommandFavorite> list(UUID clusterId, String actor) {
        requireCluster(clusterId);
        return repository.findVisible(clusterId, actor);
    }

    @Override
    public CommandFavorite create(UUID clusterId, String name, String description, String command, String namespace,
                                  boolean shared, int sortOrder, String actor) {
        requireCluster(clusterId);
        validate(name, command, namespace);
        Instant now = clock.instant();
        return repository.save(CommandFavorite.create(clusterId, actor, name.trim(), trim(description), command.trim(),
                normalize(namespace), shared, sortOrder, now));
    }

    @Override
    public CommandFavorite update(UUID clusterId, UUID favoriteId, String name, String description, String command,
                                  String namespace, boolean shared, int sortOrder, String actor) {
        validate(name, command, namespace);
        CommandFavorite existing = owned(clusterId, favoriteId, actor);
        return repository.save(existing.update(name.trim(), trim(description), command.trim(), normalize(namespace),
                shared, sortOrder, clock.instant()));
    }

    @Override
    public void delete(UUID clusterId, UUID favoriteId, String actor) {
        owned(clusterId, favoriteId, actor);
        repository.deleteById(favoriteId);
    }

    private CommandFavorite owned(UUID clusterId, UUID favoriteId, String actor) {
        CommandFavorite favorite = repository.findById(favoriteId)
                .orElseThrow(() -> new NoSuchElementException("Command favorite not found: " + favoriteId));
        if (!favorite.clusterId().equals(clusterId) || !favorite.ownerUserId().equals(actor)) {
            throw new NoSuchElementException("Command favorite not found: " + favoriteId);
        }
        return favorite;
    }

    private void validate(String name, String command, String namespace) {
        if (name == null || name.isBlank() || name.length() > 80) throw new IllegalArgumentException("favorite name must be 1 to 80 characters");
        tokenizer.validate(command, normalize(namespace));
    }

    private void requireCluster(UUID clusterId) {
        clusterRepository.findById(clusterId).orElseThrow(() -> new NoSuchElementException("Cluster not found: " + clusterId));
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() || "__ALL__".equals(value) ? null : value;
    }
}

