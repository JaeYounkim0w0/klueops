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

    /** JpaCommandFavoriteRepositoryAdapter 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    public JpaCommandFavoriteRepositoryAdapter(CommandFavoriteJpaRepository repository) {
        this.repository = repository;
    }

    /** JpaCommandFavoriteRepositoryAdapter의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    @Override
    public CommandFavorite save(CommandFavorite favorite) {
        return repository.save(CommandFavoriteEntity.fromDomain(favorite)).toDomain();
    }

    /** JpaCommandFavoriteRepositoryAdapter의 findById 처리 결과를 조회해 반환한다. */
    @Override
    public Optional<CommandFavorite> findById(UUID id) {
        return repository.findById(id).map(CommandFavoriteEntity::toDomain);
    }

    /** JpaCommandFavoriteRepositoryAdapter의 findVisible 처리 결과를 조회해 반환한다. */
    @Override
    public List<CommandFavorite> findVisible(UUID clusterId, String owner) {
        return repository.findByClusterIdAndOwnerUserIdOrClusterIdAndSharedTrueOrderBySortOrderAscUpdatedAtDesc(
                        clusterId, owner, clusterId).stream()
                .map(CommandFavoriteEntity::toDomain)
                .distinct()
                .toList();
    }

    /** JpaCommandFavoriteRepositoryAdapter의 deleteById 처리 대상과 관련 상태를 안전하게 정리한다. */
    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }
}
