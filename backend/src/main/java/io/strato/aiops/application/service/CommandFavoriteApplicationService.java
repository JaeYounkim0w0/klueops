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

    /** CommandFavoriteApplicationService 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public CommandFavoriteApplicationService(CommandFavoriteRepositoryPort repository,
                                             ClusterRepositoryPort clusterRepository,
                                             KubectlCommandTokenizer tokenizer,
                                             Clock clock) {
        this.repository = repository;
        this.clusterRepository = clusterRepository;
        this.tokenizer = tokenizer;
        this.clock = clock;
    }

    /** CommandFavoriteApplicationService의 list 처리 결과를 조회해 반환한다. */
    @Override
    public List<CommandFavorite> list(UUID clusterId, String actor) {
        requireCluster(clusterId);
        return repository.findVisible(clusterId, actor);
    }

    /** CommandFavoriteApplicationService의 create 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public CommandFavorite create(UUID clusterId, String name, String description, String command, String namespace,
                                  boolean shared, int sortOrder, String actor) {
        requireCluster(clusterId);
        validate(name, command, namespace);
        Instant now = clock.instant();
        return repository.save(CommandFavorite.create(clusterId, actor, name.trim(), trim(description), command.trim(),
                normalize(namespace), shared, sortOrder, now));
    }

    /** CommandFavoriteApplicationService의 update 처리 대상의 상태를 갱신한다. */
    @Override
    public CommandFavorite update(UUID clusterId, UUID favoriteId, String name, String description, String command,
                                  String namespace, boolean shared, int sortOrder, String actor) {
        validate(name, command, namespace);
        CommandFavorite existing = owned(clusterId, favoriteId, actor);
        return repository.save(existing.update(name.trim(), trim(description), command.trim(), normalize(namespace),
                shared, sortOrder, clock.instant()));
    }

    /** CommandFavoriteApplicationService의 delete 처리 대상과 관련 상태를 안전하게 정리한다. */
    @Override
    public void delete(UUID clusterId, UUID favoriteId, String actor) {
        owned(clusterId, favoriteId, actor);
        repository.deleteById(favoriteId);
    }

    /** CommandFavoriteApplicationService의 owned 처리에 필요한 업무 로직을 수행한다. */
    private CommandFavorite owned(UUID clusterId, UUID favoriteId, String actor) {
        CommandFavorite favorite = repository.findById(favoriteId)
                .orElseThrow(() -> new NoSuchElementException("Command favorite not found: " + favoriteId));
        if (!favorite.clusterId().equals(clusterId) || !favorite.ownerUserId().equals(actor)) {
            throw new NoSuchElementException("Command favorite not found: " + favoriteId);
        }
        return favorite;
    }

    /** CommandFavoriteApplicationService의 validate 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void validate(String name, String command, String namespace) {
        if (name == null || name.isBlank() || name.length() > 80) throw new IllegalArgumentException("favorite name must be 1 to 80 characters");
        tokenizer.validate(command, normalize(namespace));
    }

    /** CommandFavoriteApplicationService의 requireCluster 처리 입력과 현재 상태의 유효성을 검증한다. */
    private void requireCluster(UUID clusterId) {
        clusterRepository.findById(clusterId).orElseThrow(() -> new NoSuchElementException("Cluster not found: " + clusterId));
    }

    /** CommandFavoriteApplicationService의 trim 처리에 필요한 업무 로직을 수행한다. */
    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** CommandFavoriteApplicationService의 normalize 처리 데이터를 필요한 표현으로 변환한다. */
    private String normalize(String value) {
        return value == null || value.isBlank() || "__ALL__".equals(value) ? null : value;
    }
}

