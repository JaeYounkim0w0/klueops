package io.strato.aiops.application.port.out;

import io.strato.aiops.domain.command.CommandFavorite;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommandFavoriteRepositoryPort {
    /** CommandFavoriteRepositoryPort의 save 처리에 필요한 데이터를 생성하거나 저장한다. */
    CommandFavorite save(CommandFavorite favorite);
    /** CommandFavoriteRepositoryPort의 findById 처리 결과를 조회해 반환한다. */
    Optional<CommandFavorite> findById(UUID id);
    /** CommandFavoriteRepositoryPort의 findVisible 처리 결과를 조회해 반환한다. */
    List<CommandFavorite> findVisible(UUID clusterId, String owner);
    /** CommandFavoriteRepositoryPort의 deleteById 처리 대상과 관련 상태를 안전하게 정리한다. */
    void deleteById(UUID id);
}

